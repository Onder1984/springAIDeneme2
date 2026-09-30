# Taktik C2 Elektronik Harp (EW) Copilot - Büyük Veri ve PostgreSQL Mimari Dokümantasyonu

Bu doküman, **`AntigravitySpringAIDeneme2`** projesinde hayata geçirilen Docker tabanlı kalıcı PostgreSQL mimarisini, Spring Data JPA katmanını, önlemli Spring AI büyük veri araçlarını ve kullanım kılavuzunu detaylandırır.

---

## 1. MİMARİ VE ÇALIŞMA ALTYAPISI

```
┌─────────────────────────────────────────────────────────────┐
│          Kullanıcı / C2 Dashboard Arayüzü (Port 8081)       │
│          - Chat Akışı & Markdown Render                     │
│          - Chart.js (Doughnut, Bar, Radar, Line, Pie)        │
│          - Telemetri Akordiyonu & Hızlı Sorgu Paneli        │
└──────────────────────────────┬──────────────────────────────┘
                               │ HTTP REST (/api/v1/tactical)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│          Spring Boot 3.3.13 / Java 21 İcraat Katmanı        │
│          - TacticalController                               │
│          - TacticalAgentService (Spring AI 1.1.8)           │
│          - RadarEmissionTools (Önlemli Büyük Veri Araçları) │
└──────────────────────────────┬──────────────────────────────┘
                               │ JPA / Hibernate (ddl-auto: update)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│          Docker Konteyneri: tactical-postgres (Port 5432)   │
│          - İmaj: postgis/postgis:16-3.4                     │
│          - Kalıcı Volume: tactical_ew_postgres_data         │
│          - 150 EmitterFix Kaydı (Kalıcı)                    │
│          - 5.000 EmitterLob Kaydı (Kalıcı)                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. VERİ KALICILIĞI (PERSISTENCE) VE GÜVENLİK STANDARTLARI

1. **Docker Named Volume:** `tactical_ew_postgres_data` konteyner dursa veya silinse dahi diskteki verileri korur.
2. **Hibernate `ddl-auto: update`:** Veritabanındaki tabloları ve indeksleri korur, asla `create-drop` yapmaz.
3. **Akıllı Veri Başlatıcı (`DataInitializer`):**
   - Açılışta `fixJpaRepository.count()` kontrolü yapar.
   - Eğer veritabanında kayıt varsa *"Mevcut veriler korunuyor"* diyerek veri bütünlüğünü muhafaza eder.
4. **Türkçe Karakter / JVM Locale Güvencesi:**
   - Spring AI'ın Google GenAI'a gönderdiği tool şemasında `INTEGER` ve `STRING` tiplerinin ASCII standardında iletilmesi için JVM dili İngilizce'ye sabitlenmiştir (`Locale.setDefault(Locale.ENGLISH)`).

---

## 3. ÖNLEMLİ TOOL MİMARİSİ (LLM KORUMA KALKANI)

| Araç Adı | Amaç | Büyük Veri Koruma Mekanizması & Savunma Mantığı |
| :--- | :--- | :--- |
| `getTacticalSummaryStats()` | Sahadaki kestirim ve LOB makro istatistikleri | Doğrudan SQL `COUNT` ve `GROUP BY` çalıştırır; satır satır veri belleğe çekilmez. |
| `queryPriorityEmitters(limit)` | En tehlikeli atış kontrol hedefleri | En yüksek tehdit seviyesi ve son görülme zamanına göre en kritik ilk 15 hedefi döner. |
| `queryEmitterFixesPaged(...)` | Sayfalı ve dinamik kriterli radar listeleme | `cleanParam()` filtresi ile sanitize edilmiş bant, durum, radar tipi, platform ve tehdit seviyesine göre JPA Specification ile `PagedResult` döner. |
| `getNextPageOfFixes()` | Sonraki radar sayfasını otomatik çekme | Önceki filtreleri (bant, durum, radar tipi vb.) oturum hafızasından hatırlar ve bir sonraki sayfayı (page + 1) çeker. |
| `getPrevPageOfFixes()` | Önceki radar sayfasına dönme | Önceki sayfayı (page - 1) güvenli şekilde çeker. |
| `countEmitterLobs(...)` | Ham LOB sinyali sayımı ve sensör yükleri | 5.000 sinyali belleğe almadan doğrudan veritabanında sayar. |
| `queryEmitterLobsPaged(...)` | Sayfalı LOB sinyali inceleme | Sayfa başına 20-50 sinyallik kontrollü dilimler sunar. |
| `getNextPageOfLobs()` | Sonraki LOB sayfasını çekme | LOB sinyallerinin sonraki sayfasını çeker. |
| `getPrevPageOfLobs()` | Önceki LOB sayfasına dönme | LOB sinyallerinin önceki sayfasına döner. |
| `getContributingLobsForFix(fixId)` | Bir elipsi oluşturan kaynak sinyaller | Belirli bir hedefin doğruluğunu teyit etmek için çapraz hatları getirir. |
| `getTimeFrequencyScatterData(...)` | Zaman-Frekans spektral aktivite dağılımı | Radar tipi, platform ve banta göre son aktivite noktalarını (zaman, frekans, tehdit seviyesi, pri, darbe genişliği) hafif DTO olarak çeker. |

---

## 4. RADAR MODELİ VE PLATFORM TAKSONOMİSİ (DOMAIN MODELİ)

Saha hedeflerinin gerçeğe uygun modellenmesi için domain katmanında iki kritik taksonomi standardı uygulanmıştır:

1. **`RadarType` (Sistem / Model Seviyesi):**
   - Örnekler: `PANTSIR_S1_TRACKING`, `PATRIOT_AN_MPQ53`, `S300_FLAP_LID`, `AN_APG77`, `NEBO_M_VHF`, `KORAL_EW_RADAR`, `UNKNOWN_EMITTER`.
   - Doğrudan `EmitterFix` varlığına aittir. Her kestirim belirli bir radar tipine ve frekans imzasına eşlenir.
2. **`PlatformType` (Konuşlanma Ortamı):**
   - Değerler: `AIRBORNE` (Hava platformu), `GROUND` (Kara konuşlu), `NAVAL` (Deniz platformu), `UNKNOWN`.
3. **LOB vs. Fix Sorumluluk Ayrımı:**
   - **LOB (Line of Bearing):** Sensör düğümlerinin (ALPHA, BRAVO, vb.) yakaladığı tekil yön kestirim vektörleridir. Tek başlarına radar tipi bilmeyebilirler; füzyon motoru tarafından kesiştirilip elips oluşturulduğunda `associatedFixId` üzerinden hedefin kimliğine bağlanırlar.
   - **EmitterFix:** LOB'ların çoklu kesişiminden hesaplanan coğrafi elips, kesinleşmiş frekans, radar tipi ve platform sınıflandırmasını barındırır.

---

## 5. SAVUNMACI PARAMETRE TEMİZLEME (LLM INPUT SANITIZATION)

Büyük Dil Modelleri (LLM), serbest metinden tool çağrısı üretirken sıkça şu desenleri üretir:
- Filtre belirtilmediğinde veya kullanıcı "tüm radarlar" dediğinde: `radarType="*"`, `band="ALL"`, `status="ANY"`, `platformType="NULL"`.
- Bu değerler doğrudan SQL/JPA sorgusuna girerse sorgu boş döner ve sistem yanıt veremez.

**Mimari Standart:**
Tüm Spring AI Tool metodlarında giriş parametreleri merkezi `cleanParam` fonksiyonundan geçirilmelidir:
```java
private String cleanParam(String val) {
    if (val == null) return null;
    String trimmed = val.trim();
    if (trimmed.isEmpty() ||
        trimmed.equalsIgnoreCase("ALL") ||
        trimmed.equalsIgnoreCase("ANY") ||
        trimmed.equalsIgnoreCase("NULL") ||
        trimmed.equalsIgnoreCase("NONE") ||
        trimmed.equalsIgnoreCase("*") ||
        trimmed.equalsIgnoreCase("TÜMÜ") ||
        trimmed.equalsIgnoreCase("HEPSİ")) {
        return null;
    }
    return trimmed;
}
```
Bu sayede parametre `null`a dönüşür ve JPA Specification katmanında dinamik olarak `cb.conjunction()` (filtresiz / tümü) moduna geçer.

---

## 6. ZAMAN-FREKANS DAĞILIMI VE CHART.JS SCATTER ŞELALE ANALİTİĞİ

Zaman serisi ve frekans eksenli spektral aktivite analizlerinde yaşanan en kritik görselleştirme tuzağı ve mimari çözümü:

1. **Zaman String'i Tuzağı:**
   - Chart.js `scatter` grafiğinde X eksenine `"19:04"` gibi metin tabanlı saatler verilirse, Chart.js linear scale `Number("19:04") -> NaN` hatası verir ve grafik tamamen boş görünür.
2. **Dakika Tabanlı Sürekli Zaman Çizgisi Çözümü:**
   - X ekseni gece yarısından itibaren **dakika cinsinden sayısal değere (`hours * 60 + minutes`)** dönüştürülür.
   - Bu sayede 1440 dakikalık (24 saat) fiziksel bir zaman ekseni elde edilir ve zamansal boşluklar (aralıklar) gerçeğe uygun şekilde korunur.
   - X ekseni tick callback'inde bu dakika değeri tekrar `"HH:mm"` string'ine formatlanır.
3. **Deep Scale Merge İhtiyacı:**
   - Grafik konfigürasyonu güncellenirken `Object.assign` yüzeysel (shallow) kopyalama yaptığı için `ticks`, `grid` ve `type` ayarlarını ezebilir; eksen ayarları daima derinleme (deep merge) korunmalıdır.
4. **Tehdit Dereceli Renk Paleti:**
   - Tehdit seviyesine (1-5) göre renk kodlaması: 5 (Kritik Kırmızı `#ff0055`), 4 (Yüksek Turuncu `#ff8800`), 3 (Orta Sarı `#ffcc00`), 2 (Düşük Mavi `#00d4ff`), 1 (İzleme Yeşil `#00ff88`).

---

## 7. ÇİFT YÖNLÜ SAYFALAMA MİMARİSİ (DUAL PAGINATION ENGINE)

Kullanıcının tercihine göre iki farklı sayfalama mekanizması eşzamanlı olarak çalışmaktadır:

### Yöntem 1: Arayüzden İnteraktif Sayfalama (On-Screen Interactive UI)
1. **Chat Yanıt Kartı Butonları:**
   - Çoklu kayıt döndüğünde mesaj balonunun altında otomatik olarak HUD navigasyon çubuğu belirir.
   - `[⬅️ Önceki Sayfa]`, `[📄 Sayfa X / Y Rozeti]`, `[➡️ Sonraki 15 Hedefi Getir]` butonları doğrudan tıklanabilir.
2. **Sağ Radar Envanter Paneli (RADAR LİSTESİ):**
   - 150 adet taktik radar hedefini sayfa başına 15 adet olacak şekilde ekranda filtreler ve sayfalar.
   - Kullanıcı `[⬅️ Önceki]` ve `[Sonraki ➡️]` butonlarıyla arayüzden anında sonraki 15 hedefe geçebilir.

### Yöntem 2: Doğal Dil / Sesli / Multi-Turn Sohbet Sayfalaması (Conversational Pagination)
1. **Spring AI `MessageChatMemoryAdvisor`:**
   - Önceki konuşma turunu (örn. "Aktif X-Band radarlarını listele") bellekte tutar.
2. **Oturum Güvenli Sayfalama Hafızası (`SESSION_STATES`):**
   - ThreadLocal yerine `ConcurrentHashMap` kullanılarak her oturum (`conversationId`) için son filtreler (`lastBand`, `lastStatus`, `lastRadarType`, `lastFixPage`) thread'ler arası güvenle korunur.
3. **Doğal Dil Komutları:**
   - Operatör *"bana sonraki sayfayı da ver"*, *"sıradaki hedefleri göster"* veya *"devamını getir"* dediğinde Gemini otomatik olarak `getNextPageOfFixes()` aracını tetikler.
   - *"Önceki sayfaya dön"* dediğinde `getPrevPageOfFixes()` aracı çağrılır.
   - Operatör kriterleri tekrar yazmak zorunda kalmaz.

---

## 8. REST API UÇ NOKTALARI (PORT 8081)

- `GET /api/v1/tactical/stats` -> Canlı makro istatistikler (Toplam Fix, LOB, Bant Dağılımı, Sensör Yükleri, Radar Tipi ve Platform Dağılımları).
- `GET /api/v1/tactical/fixes` -> Tam radar envanter listesi veya `?page=0&size=15&band=X&radarType=PANTSIR_S1_TRACKING` ile sayfalı ve filtreli liste.
- `GET /api/v1/tactical/lobs?page=0&size=20` -> Sayfalı LOB sinyalleri.
- `GET /api/v1/tactical/time-frequency?limit=300` -> Zaman-Frekans spektral dağılım ham veri noktaları (Scatter / Waterfall).
- `POST /api/v1/tactical/analyze` -> LLM doğal dil analizi, tool çağrıları, telemetri logları ve Chart.js sentezi.

---

## 9. GÜVENLİK VE SECRET YÖNETİMİ (ZERO SECRETS IN CODEBASE)

1. **Kod Deposu Temizliği:**
   - Kaynak kodlarda, `.yml` veya `.properties` dosyalarında hiçbir zaman hardcoded API anahtarı barındırılmaz.
2. **Çevresel Değişken Önceliği:**
   - `application.yml` konfigürasyonu yedekli ortam değişkeni hiyerarşisi kullanır:
     ```yaml
     spring:
       ai:
         openai:
           # veya google-genai
           api-key: ${GEMINI_API_KEY:${GOOGLE_API_KEY:}}
     ```
3. **Lokal Geliştirme Ortamı:**
   - Anahtarlar OS seviyesinde (`[System.Environment]::SetEnvironmentVariable('GEMINI_API_KEY', '...', 'User')`) tanımlanır ve terminal/IDE üzerinden otomatik okunur.

---

## 10. ÇALIŞTIRMA VE TEST KOMUTLARI

```powershell
# 1. PostgreSQL Konteynerini Başlatma
docker compose up -d

# 2. Testleri Çalıştırma (Veritabanı Entegrasyon Testi)
C:\tools\apache-maven-3.9.11\bin\mvn.cmd test

# 3. Uygulamayı Başlatma (Port 8081)
C:\tools\apache-maven-3.9.11\bin\mvn.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-Duser.language=en -Duser.country=US"
```

---

## 11. MİMARİ VİZYON: "GÖZ (VIEWING)" İLE "AKIL (REASONING)" AYRIMI

Taktik Elektronik Harp ve Komuta Kontrol (C2) projelerinde LLM'in veri listeleme aracı olarak kullanılmasını önleyen temel prensip:

1. **"Göz" Katmanı (Sanallaştırılmış UI & REST):**
   * **Görev:** Milyonlarca LOB / Fix satırını operatöre 60 FPS hızla sunmak, kolon bazlı anlık süzmek, haritada GPU hızlandırmasıyla çizdirmek.
   * **Önerilen Teknolojiler:** **TanStack Table (Virtual) / AG-Grid**, **MapLibre GL JS / Deck.gl**, **Spring WebFlux / SSE / WebSocket**.
2. **"Akıl" Katmanı (Spring AI Copilot):**
   * **Görev:** Ham satırları tek tek dökmek yerine; büyük resmi görmek, anomali tespiti yapmak, çapraz korelasyon kurmak ve taktik karar desteği sağlamak.
   * **Önerilen Teknolojiler:** **Spring AI (Tool Calling)**, **Google Gemini (1.5 / 2.0 Flash)**, **PostgreSQL 16 + PostGIS + TimescaleDB (Continuous Aggregates)**.
3. **Sayfalamanın Doğru Konumu:**
   * Sayfalama LLM'in ana işi değil; LLM'in context penceresini patlamaktan koruyan **güvenlik sübabı (safety guardrail)** rolündedir.

