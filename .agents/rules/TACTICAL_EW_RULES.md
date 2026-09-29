# Taktik C2 Elektronik Harp (EW) & Büyük Veri Kuralları

Bu dosya, `AntigravitySpringAIDeneme2` projesinin mimari standartlarını, büyük veri koruma kurallarını ve geliştirme yönergelerini belirler.

---

## 1. MİMARİ VE ÇALIŞMA ALTYAPISI

- **Dil ve Çatı:** Java 21 LTS, Spring Boot 3.3.13, Spring Data JPA, Spring AI 1.1.8 (Google GenAI).
- **Veritabanı:** Docker üzerinde çalışan `postgis/postgis:16-3.4` (PostgreSQL 16 + PostGIS 3.4).
- **Port:** Uygulama `8081` portunda çalışır (İlk faz in-memory projesi ile çakışmayı önlemek için).
- **Veri Kalıcılığı (Data Persistence):**
  - Docker Compose içinde `tactical_ew_postgres_data` isimli kalıcı named volume tanımlıdır.
  - `spring.jpa.hibernate.ddl-auto: update` kullanılır; tablolar asla silinmez/sıfırlanmaz.
  - `DataInitializer.java` başlangıçta mevcut kayıtları kontrol eder; veritabanında veri varsa asla ezmez ve mükerrer eklemez.
- **Türkçe Karakter / JVM Locale Kuralı:**
  - Google GenAI JSON şemalarında `type` alanının `"INTEGER"` ve `"STRING"` olması zorunludur.
  - Türkçe yerel ayarında `i.toUpperCase() -> 'İ'` dönüştüğü için Google API 400 Bad Request verir.
  - Bu sebeple `TacticalEwApplication.java` içinde `Locale.setDefault(Locale.ENGLISH)` ve `.mvn/jvm.config` içinde `-Duser.language=en -Duser.country=US` ayarları kalıcı olarak korunmalıdır.

---

## 2. BÜYÜK VERİ & LLM SAVUNMA MEKANİZMASI (DEFENSIVE TOOLING)

Veritabanında 150 adet taktik Fix ve 5.000 adet ham LOB sinyali bulunmaktadır:
1. **Makro Sorularda Doğrudan SQL Agregasyonu:**
   - *"Genel durum nedir?"*, *"Bant dağılımı ver"* gibi sorularda satır satır veri çekilmez.
   - `RadarEmissionTools.getTacticalSummaryStats()` ve `countEmitterLobs()` araçları çağrılır; veritabanı motoru `COUNT` ve `GROUP BY` ile milisaniyede özet döner.
2. **Öncelikli Tehdit Sıralaması (Top-K Ceiling):**
   - *"Kritik hedefleri listele"* dendiğinde `RadarEmissionTools.queryPriorityEmitters(limit=15)` çağrılır.
   - En yüksek tehdit seviyesine (Ku/X-Band, canlılık, hata payı) göre en kritik 15 hedef çekilir; modelin context penceresi şişirilmez.
3. **Güvenli Sayfalama (`PagedResult<T>`) ve Çift Yönlü Sayfalama (Dual Pagination):**
   - **Yöntem 1 (Ekran Butonu):** Kullanıcı arayüzde doğrudan butonlara (`[Önceki]`, `[Sonraki 15 Hedefi Getir]`) basarak sayfalar arası geçiş yapabilir. Sağ paneldeki radar çekmecesinde 150 radar 15'erli sayfalar halinde anında taranabilir.
   - **Yöntem 2 (Doğal Dil / Multi-Turn):** Kullanıcı *"bana sonraki sayfayı da ver"*, *"devamını getir"* veya *"sıradaki hedefleri göster"* dediğinde Gemini `getNextPageOfFixes()` / `getNextPageOfLobs()` araçlarını çağırır.
   - Önceki filtreler `ConcurrentHashMap` tabanlı `SESSION_STATES` oturum hafızasında güvenle saklanır, filtreler unutulmadan bir sonraki sayfa (page + 1) veya önceki sayfa (page - 1) getirilir.

---

## 3. ASKERİ DOĞRULUK VE BRİFİNG STANDARTLARI

- **Determinizm:** Model asla kafasından koordinat veya istatistik uyduramaz; her bilgi tool çağrısı ile kanıtlanmalıdır.
- **Hata Elipsi Sınıflandırması:**
  - `semiMajorAxisMeters <= 1500m`: Yüksek Doğruluklu / Teyitli Atış Kontrol.
  - `semiMajorAxisMeters > 4000m`: Düşük Doğruluklu / Şüpheli / İlave Tarama Gerekir.
- **Genel Durum Yanıt Formatı (4 Ana Başlık):**
  1. `## OPERASYONEL ÖZET`
  2. `## ÖNCELİKLİ TEHDİTLER VE DOĞRULUK DEĞERLENDİRMESİ`
  3. `## AÇIKTA KALAN LOB HATLAR VE SENSÖR YÜKLERİ`
  4. `## HAREKÂT ÖNERİLERİ`

---

## 4. İNTERAKTİF GRAFİK YETENEĞİ (CHART.JS PROTOKOLÜ)

Kullanıcı görselleştirme veya grafik istediğinde, yanıtın sonuna geçerli bir ` ```json:chart ` kod bloğu eklenir:
- Desteklenen tipler: `doughnut`, `pie`, `bar`, `radar`, `line`.
- Veriler sahadan toplanan gerçek SQL istatistikleriyle beslenir.
