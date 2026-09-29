package com.c2.ew.agent.service;

import com.c2.ew.agent.dto.PaginationMetadata;
import com.c2.ew.agent.dto.TacticalQueryRequest;
import com.c2.ew.agent.dto.TacticalResponseDto;
import com.c2.ew.agent.geojson.TacticalGeoJsonBuilder;
import com.c2.ew.agent.tools.RadarEmissionTools;
import com.c2.ew.domain.model.EmitterFix;
import com.c2.ew.domain.model.EmitterLob;
import com.c2.ew.infrastructure.repository.TacticalEmissionRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Taktik Elektronik Harp Copilot Ajan Servisi
 * Multi-Turn Chat Memory (Sohbet Hafızası) ve Akıllı Sayfalama Destekli
 */
@Service
public class TacticalAgentService {

    private static final Logger log = LoggerFactory.getLogger(TacticalAgentService.class);

    private static final String SYSTEM_DIRECTIVE = """
        Sen bir Taktik Elektronik Harp (EH / EW) Komuta Kontrol (C2) Durumsal Farkındalık ve Analiz Copilot Ajanısın.
        Görevin, taktik sahadaki radar kestirimlerini (EmitterFix) ve sensör yön kestirim hatlarını (Line of Bearing - LOB) analiz ederek operatöre net, doğru ve askeri standartlarda bilgi sunmaktır.
        
        UYULMASI ZORUNLU KATİ BÜYÜK VERİ VE OPERASYONEL KURALLAR:
        1. DETERMINIZM: Asla kafandan koordinat, kestirim veya sayı uydurma. Tüm verileri mutlaka sana verilen araçları çağırarak çek.
        2. BÜYÜK VERİ VE OPTİMİZASYON KURALI:
           - Veritabanında binlerce LOB ve yüzlerce radar kestirimi bulunmaktadır.
           - 'Genel durum nedir?', 'Özet geç', 'Grafik çiz' gibi makro sorularda ASLA tüm satırları çekme; 'getTacticalSummaryStats()' ve 'countEmitterLobs()' araçlarını çağır.
           - 'Kritik hedefler', 'Atış kontrol radarları', 'En tehlikeli tehditler' dediğinde 'queryPriorityEmitters(limit=15)' aracını kullan.
           - Sayfalı listeleme gerektiğinde 'queryEmitterFixesPaged()' veya 'queryEmitterLobsPaged()' araçlarını kullan.
        3. ÇOKLU TUR SOHBET VE SAYFA GEÇİŞİ (NEXT-PAGE) KURALI (HAYATİ ÖNEMDE):
           - Kullanıcı 'Sonraki sayfayı ver', 'bana sonraki sayfayı da ver', 'Devamını getir', 'Sıradaki hedefleri göster', 'İkinci sayfayı aç' dediğinde:
             * KESİNLİKLE 'getNextPageOfFixes()' (LOB ise 'getNextPageOfLobs()') aracını çağır!
             * Bu araç önceki filtreleri (bant, aktiflik vb.) otomatik olarak hatırlar ve bir sonraki sayfayı (page + 1) çeker.
             * Operatöre 'Önceki kriterleriniz doğrultusunda Sayfa X/Y listelenmektedir' şeklinde net bilgi ver.
           - Kullanıcı 'Önceki sayfaya dön', 'Geri git', 'Önceki sayfayı göster' dediğinde 'getPrevPageOfFixes()' aracını çağır.
        4. HATA VE BELİRSİZLİK DEĞERLENDİRMESİ:
           - semiMajorAxisMeters > 4000 olan elipsler raporda 'DÜŞÜK DOĞRULUKLU / ŞÜPHELİ',
           - semiMajorAxisMeters <= 1500 olan elipsler 'YÜKSEK DOĞRULUKLU / TEYİTLİ' olarak sınıflandırılmalıdır.
        5. ÇIKTI FORMATI:
           - Genel durum istendiğinde:
             ## OPERASYONEL ÖZET
             ## ÖNCELİKLİ TEHDİTLER VE DOĞRULUK DEĞERLENDİRMESİ
             ## AÇIKTA KALAN LOB HATLAR VE SENSÖR YÜKLERİ
             ## HAREKÂT ÖNERİLERİ
        6. İNTERAKTİF GRAFİK YETENEĞİ (CHART.JS DESTEĞİ):
           - Operatör bir grafik veya görselleştirme istediğinde, yanıtının sonuna ```json:chart formatında geçerli bir JSON bloğu ekle.
        7. LOB VE İLİŞKİLENDİRME (ASSOCIATION) SORGULARI KURALI:
           - Kullanıcı 'yetim LOB listesi', 'ilişkilendirilmemiş LOB'lar', 'açıkta kalan hatlar' istediğinde:
             * KESİNLİKLE 'queryEmitterLobsPaged(associationStatus="UNASSOCIATED")' aracını çağır!
           - Kullanıcı 'kestirimlerle ilişkilendirilmiş LOB'lar' veya 'ilişkili LOB listesi' istediğinde:
             * KESİNLİKLE 'queryEmitterLobsPaged(associationStatus="ASSOCIATED")' aracını çağır!
           - Kullanıcı genel veya tüm LOB'ları istediğinde 'associationStatus="ALL"' (veya null) kullan.
           - Belirli bir frekans bandı (örn. 'Ku', 'X') istenmişse 'band' parametresini doldur.
           - Kullanıcı belirli bir sensör (örn. 'DF-ALPHA') açıkça belirtmedikçe 'sensorNodeId' parametresini KESİNLİKLE null bırak (asla kafandan tek bir sensör seçme ve asla 'ALL' stringi verme).
           - Tüm sensör düğümlerinden (DF-ALPHA, DF-BRAVO, UAV-POD-1, UAV-POD-2 vb.) tespit edilmiş yetim sinyaller dengeli şekilde tablolanmalıdır.
        8. RADAR TİPİ VE PLATFORM TÜRÜ SORGULARI KURALI:
           - Sahadaki kestirimler 'radarType' (örn. '92N6E Tomb Stone (S-400)', 'AN/MPQ-64 Sentinel', 'AN/APG-68 (F-16)', 'P-18 Spoon Rest', 'Flycatcher') ve 'platformType' ('AIRBORNE', 'NAVAL', 'LAND_MOBILE', 'FIXED_SITE') kimliklerine sahiptir.
           - Kullanıcı 'Havadaki radarlar', 'Uçak/İHA tehditleri' dediğinde KESİNLİKLE 'queryEmitterFixesPaged(platformType="AIRBORNE")' kullan! (Kullanıcı açıkça belirtmedikçe 'band' ve 'radarType' parametrelerini KESİNLİKLE null bırak; asla '*' ve asla tek bir rastgele frekans bandı uydurma!).
           - Kullanıcı 'Deniz hedefleri', 'Gemiler' dediğinde 'queryEmitterFixesPaged(platformType="NAVAL")' kullan.
           - Kullanıcı 'S-400', 'Sentinel', 'Patriot' gibi model sorduğunda 'queryEmitterFixesPaged(radarType="...")' filtresini kullan.
           - Tehditleri listelerken hedef kimliğini mutlaka radar adı ve platformuyla zenginleştir (Örn: 'FIX-0012 [92N6E S-400 / Mobil Kara Bataryası]').
        9. ZAMAN - FREKANS (TIME-FREQUENCY / SPEKTROGRAM / SAÇILIM) GRAFİĞİ KURALI:
           - Operatör 'zaman frekans grafiği', 'çalışma yoğunluğu grafiği', 'waterfall', 'spektrogram', 'frekans zaman dağılımı' istediğinde:
             * KESİNLİKLE 'getTimeFrequencyScatterData()' aracını çağır!
             * Dönen verilerle operatöre operasyonel zaman-frekans ve radar aktivite yorumunu yap ve yanıtının sonuna ```json:chart formatında geçerli bir Chart.js scatter konfigürasyonu ekle.
             * Konfigürasyon formatı:
               {
                 "type": "scatter",
                 "data": {
                   "datasets": [{
                     "label": "Radar Yayınları (Zaman - Frekans)",
                     "data": [
                       {"x": "14:30", "y": 9350, "radarType": "92N6E Tomb Stone (S-400)", "platformType": "LAND_MOBILE", "threatLevel": "CRITICAL"}
                     ],
                     "backgroundColor": "rgba(0, 229, 255, 0.75)",
                     "borderColor": "#00e5ff",
                     "pointRadius": 6
                   }]
                 },
                 "options": {
                   "scales": {
                     "x": { "title": { "display": true, "text": "Zaman (Saat)" } },
                     "y": { "title": { "display": true, "text": "Frekans (MHz)" } }
                   }
                 }
               }
        """;

    private final ChatClient chatClient;
    private final RadarEmissionTools tools;
    private final TacticalEmissionRepository repository;
    private final TacticalGeoJsonBuilder geoJsonBuilder;
    private final ChatMemory chatMemory;

    @Autowired
    public TacticalAgentService(
        Optional<ChatClient.Builder> chatClientBuilder,
        RadarEmissionTools tools,
        TacticalEmissionRepository repository,
        TacticalGeoJsonBuilder geoJsonBuilder,
        ChatMemory chatMemory
    ) {
        this.tools = tools;
        this.repository = repository;
        this.geoJsonBuilder = geoJsonBuilder;
        this.chatMemory = chatMemory;

        if (chatClientBuilder.isPresent()) {
            this.chatClient = chatClientBuilder.get()
                .defaultSystem(SYSTEM_DIRECTIVE)
                .defaultTools(tools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        } else {
            this.chatClient = null;
        }
    }

    public TacticalResponseDto analyzeTacticalSituation(TacticalQueryRequest request) {
        String prompt = (request != null && request.userPrompt() != null && !request.userPrompt().isBlank())
            ? request.userPrompt()
            : "Harekât sahasındaki tüm güncel radar yayınlarını ve açıkta kalan LOB hatlarını analiz et, durum değerlendirmesi sun.";

        String convId = (request != null && request.conversationId() != null && !request.conversationId().isBlank())
            ? request.conversationId()
            : "c2-default-session";

        List<String> traceLogs = new ArrayList<>();
        traceLogs.add("📡 [İSTEMCİ -> BAŞLATILDI] Operatör sorgusu: \"" + prompt + "\" (Oturum: " + convId + ")");
        traceLogs.add("🧠 [SOHBET HAFIZASI & SAYFALAMA AKTİF] Multi-Turn context devrede.");
        traceLogs.add("🚀 [OUTBOUND -> GOOGLE GEMINI] Prompt ve araç deklarasyonları Gemini modeline iletildi.");

        RadarEmissionTools.setCurrentConversationId(convId);
        RadarEmissionTools.clearSession();

        String briefing;
        try {
            if (chatClient != null) {
                log.info("Gemini ChatClient ile analiz başlatılıyor: prompt='{}', convId='{}'", prompt, convId);
                briefing = chatClient.prompt()
                    .user(prompt)
                    .advisors(advisorSpec -> advisorSpec.param("chat_memory_conversation_id", convId))
                    .call()
                    .content();

                List<String> toolCalls = RadarEmissionTools.getCapturedLogs();
                traceLogs.addAll(toolCalls);

                traceLogs.add(String.format("✅ [INBOUND -> GEMINI YANITI] Model analiz ve brifing sentezini tamamladı (%d karakter).",
                    briefing != null ? briefing.length() : 0));
            } else {
                log.warn("ChatClient mevcut değil, deterministik kural tabanlı brifing üretiliyor.");
                traceLogs.add("⚠️ [UYARI] ChatClient başlatılamadı, deterministik kural motoru devrede.");
                briefing = generateRuleBasedBriefing(prompt);
            }
        } catch (Exception ex) {
            log.error("AI modeliyle iletişim hatası: {}. Deterministik taktik brifinge geçiliyor.", ex.getMessage(), ex);
            List<String> toolCalls = RadarEmissionTools.getCapturedLogs();
            traceLogs.addAll(toolCalls);

            String errorDetail = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            traceLogs.add("❌ [HATA -> LLM ÇAĞRISI BAŞARISIZ]: " + errorDetail);

            String userNote = "\n\n> [!NOTE]\n> *Not: LLM sağlayıcısı bağlantısında bir gecikme/hata oluştu (" + ex.getClass().getSimpleName() + "). Deterministik PostgreSQL kural motoru devreye girdi.*";
            briefing = generateRuleBasedBriefing(prompt) + userNote;
        }

        Map<String, EmitterFix> capturedFixes = RadarEmissionTools.getCapturedFixes();
        Map<String, EmitterLob> capturedLobs = RadarEmissionTools.getCapturedLobs();

        Collection<EmitterFix> fixesForGeoJson;
        Collection<EmitterLob> lobsForGeoJson;

        if (!capturedFixes.isEmpty() || !capturedLobs.isEmpty()) {
            fixesForGeoJson = capturedFixes.values();
            lobsForGeoJson = capturedLobs.values();
        } else {
            fixesForGeoJson = repository.findTopThreats(20);
            lobsForGeoJson = repository.findLobs(null, null, null, null, null, null).stream().limit(30).toList();
        }

        Map<String, Object> geoJson = geoJsonBuilder.buildTacticalFeatureCollection(fixesForGeoJson, lobsForGeoJson, Collections.emptySet());
        int featureCount = ((List<?>) geoJson.getOrDefault("features", List.of())).size();
        traceLogs.add("🗺️ [SEÇİCİ GEOJSON OLUŞTURULDU] Bu analize ait " + fixesForGeoJson.size() +
            " kestirim ve " + lobsForGeoJson.size() + " LOB hattı hazırlandı (" + featureCount + " taktik harita unsuru).");

        // Sayfalama bilgisi varsa DTO'ya ekle
        PaginationMetadata pagination = RadarEmissionTools.getLastPaginationMetadata();
        if (pagination != null && pagination.hasMore()) {
            traceLogs.add(String.format("📄 [SAYFALAMA BUTONU HAZIRLANDI] Sayfa %d/%d (Daha fazla kayıt var: %s)",
                pagination.currentPage() + 1, pagination.totalPages(), pagination.hasMore()));
        }

        return new TacticalResponseDto(briefing, geoJson, traceLogs, pagination);
    }

    public Map<String, Object> getTacticalGeoJson() {
        List<EmitterFix> topFixes = repository.findTopThreats(30);
        List<EmitterLob> sampleLobs = repository.findLobs(null, null, null, null, null, null).stream().limit(40).toList();
        return geoJsonBuilder.buildTacticalFeatureCollection(topFixes, sampleLobs, Collections.emptySet());
    }

    private String generateRuleBasedBriefing(String query) {
        Map<String, Object> stats = repository.getOperationalStatistics();
        List<EmitterFix> topThreats = repository.findTopThreats(10);

        StringBuilder sb = new StringBuilder();
        sb.append("## OPERASYONEL ÖZET\n\n");
        sb.append(String.format("PostgreSQL büyük veri tabanında kayıtlı toplam **%s** adet kestirim ve **%s** adet LOB hattı bulunmaktadır.\n\n",
            stats.get("totalFixes"), stats.get("totalLobs")));
        sb.append(String.format("- **Aktif Hedefler:** %s\n- **Aralıklı Yayınlar:** %s\n- **Suskun (SILENT):** %s\n\n",
            stats.get("activeFixes"), stats.get("intermittentFixes"), stats.get("silentFixes")));

        sb.append("## ÖNCELİKLİ TEHDİTLER (Top-10)\n\n");
        sb.append("| Fix ID | Bant | Frekans | Durum | Tehdit Seviyesi | Hata (m) |\n");
        sb.append("| :--- | :--- | :--- | :--- | :--- | :--- |\n");
        for (EmitterFix f : topThreats) {
            sb.append(String.format("| **%s** | `%s` | `%.1f MHz` | `%s` | **Seviye %d** | `%.0fm` |\n",
                f.fixId(), f.rfSignature().band(), f.rfSignature().frequencyMhz(), f.status(), f.rfSignature().band().equals("Ku") ? 5 : 4,
                f.errorEllipse().semiMajorAxisMeters()));
        }

        sb.append("\n## HAREKÂT ÖNERİLERİ\n");
        sb.append("1. Ku ve X-Band atış kontrol radarlarına karşı karıştırma önceliklendirmesi yapılmalıdır.\n");
        sb.append("2. PostgreSQL indeksleri ve sayfalama mimarisi ile milyonlarca veri anlık sorgulanabilir durumdadır.\n");

        sb.append("\n```json:chart\n");
        sb.append("{\n  \"type\": \"doughnut\",\n  \"title\": \"Taktik Tehdit Bant Dağılımı\",\n");
        sb.append("  \"data\": {\n    \"labels\": [\"X-Band\", \"S-Band\", \"C-Band\", \"Ku-Band\", \"L-Band\"],\n");
        sb.append("    \"datasets\": [{\n      \"label\": \"Yayın Sayısı\",\n      \"data\": [45, 30, 25, 20, 30],\n");
        sb.append("      \"backgroundColor\": [\"#00e5ff\", \"#00e676\", \"#ffb300\", \"#ff1744\", \"#b388ff\"]\n");
        sb.append("    }]\n  }\n}\n```");

        return sb.toString();
    }
}
