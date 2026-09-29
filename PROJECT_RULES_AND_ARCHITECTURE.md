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

| Araç Adı | Amaç | Büyük Veri Koruma Mekanizması |
| :--- | :--- | :--- |
| `getTacticalSummaryStats()` | Sahadaki kestirim ve LOB makro istatistikleri | Doğrudan SQL `COUNT` ve `GROUP BY` çalıştırır; satır satır veri belleğe çekilmez. |
| `queryPriorityEmitters(limit)` | En tehlikeli atış kontrol hedefleri | En yüksek tehdit seviyesi ve son görülme zamanına göre en kritik ilk 15 hedefi döner. |
| `queryEmitterFixesPaged(...)` | Sayfalı radar listeleme | `PagedResult` döner. `totalCount`, `page`, `pageSize`, `hasMore` bilgisi ile bağlamı korur. |
| `getNextPageOfFixes()` | Sonraki radar sayfasını otomatik çekme | Önceki filtreleri (bant, durum vb.) oturum hafızasından hatırlar ve bir sonraki sayfayı (page + 1) çeker. |
| `getPrevPageOfFixes()` | Önceki radar sayfasına dönme | Önceki sayfayı (page - 1) güvenli şekilde çeker. |
| `countEmitterLobs(...)` | Ham LOB sinyali sayımı ve sensör yükleri | 5.000 sinyali belleğe almadan doğrudan veritabanında sayar. |
| `queryEmitterLobsPaged(...)` | Sayfalı LOB sinyali inceleme | Sayfa başına 20-50 sinyallik kontrollü dilimler sunar. |
| `getNextPageOfLobs()` | Sonraki LOB sayfasını çekme | LOB sinyallerinin sonraki sayfasını çeker. |
| `getPrevPageOfLobs()` | Önceki LOB sayfasına dönme | LOB sinyallerinin önceki sayfasına döner. |
| `getContributingLobsForFix(fixId)` | Bir elipsi oluşturan kaynak sinyaller | Belirli bir hedefin doğruluğunu teyit etmek için çapraz hatları getirir. |

---

## 4. ÇİFT YÖNLÜ SAYFALAMA MİMARİSİ (DUAL PAGINATION ENGINE)

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
   - ThreadLocal yerine `ConcurrentHashMap` kullanılarak her oturum (`conversationId`) için son filtreler (`lastBand`, `lastStatus`, `lastFixPage`) thread'ler arası güvenle korunur.
3. **Doğal Dil Komutları:**
   - Operatör *"bana sonraki sayfayı da ver"*, *"sıradaki hedefleri göster"* veya *"devamını getir"* dediğinde Gemini otomatik olarak `getNextPageOfFixes()` aracını tetikler.
   - *"Önceki sayfaya dön"* dediğinde `getPrevPageOfFixes()` aracı çağrılır.
   - Operatör kriterleri tekrar yazmak zorunda kalmaz.

---

## 5. REST API UÇ NOKTALARI (PORT 8081)

- `GET /api/v1/tactical/stats` -> Canlı makro istatistikler (Toplam Fix, LOB, Bant Dağılımı, Sensör Yükleri).
- `GET /api/v1/tactical/fixes` -> Tam radar envanter listesi veya `?page=0&size=15` ile sayfalı liste.
- `GET /api/v1/tactical/lobs?page=0&size=20` -> Sayfalı LOB sinyalleri.
- `POST /api/v1/tactical/analyze` -> LLM doğal dil analizi, tool çağrıları, telemetri logları ve Chart.js sentezi.

---

## 6. ÇALIŞTIRMA VE TEST KOMUTLARI

```powershell
# 1. PostgreSQL Konteynerini Başlatma
docker compose up -d

# 2. Testleri Çalıştırma (Veritabanı Entegrasyon Testi)
C:\tools\apache-maven-3.9.11\bin\mvn.cmd test

# 3. Uygulamayı Başlatma (Port 8081)
C:\tools\apache-maven-3.9.11\bin\mvn.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-Duser.language=en -Duser.country=US"
```

---

## 7. MİMARİ VİZYON: "GÖZ (VIEWING)" İLE "AKIL (REASONING)" AYRIMI

Taktik Elektronik Harp ve Komuta Kontrol (C2) projelerinde LLM'in veri listeleme aracı olarak kullanılmasını önleyen temel prensip:

1. **"Göz" Katmanı (Sanallaştırılmış UI & REST):**
   * **Görev:** Milyonlarca LOB / Fix satırını operatöre 60 FPS hızla sunmak, kolon bazlı anlık süzmek, haritada GPU hızlandırmasıyla çizdirmek.
   * **Önerilen Teknolojiler:** **TanStack Table (Virtual) / AG-Grid**, **MapLibre GL JS / Deck.gl**, **Spring WebFlux / SSE / WebSocket**.
2. **"Akıl" Katmanı (Spring AI Copilot):**
   * **Görev:** Ham satırları tek tek dökmek yerine; büyük resmi görmek, anomali tespiti yapmak, çapraz korelasyon kurmak ve taktik karar desteği sağlamak.
   * **Önerilen Teknolojiler:** **Spring AI (Tool Calling)**, **Google Gemini (1.5 / 2.0 Flash)**, **PostgreSQL 16 + PostGIS + TimescaleDB (Continuous Aggregates)**.
3. **Sayfalamanın Doğru Konumu:**
   * Sayfalama LLM'in ana işi değil; LLM'in context penceresini patlamaktan koruyan **güvenlik sübabı (safety guardrail)** rolündedir.
