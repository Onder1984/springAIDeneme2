package com.c2.ew.agent.service;

import com.c2.ew.agent.dto.PaginationMetadata;
import com.c2.ew.agent.dto.TacticalComintSummaryDto;
import com.c2.ew.agent.dto.TacticalEmissionSummaryDto;
import com.c2.ew.agent.dto.TacticalQueryRequest;
import com.c2.ew.agent.dto.TacticalResponseDto;
import com.c2.ew.agent.geojson.TacticalGeoJsonBuilder;
import com.c2.ew.agent.tools.ComintEmissionTools;
import com.c2.ew.agent.tools.RadarEmissionTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TacticalAgentService {

    private static final Logger log = LoggerFactory.getLogger(TacticalAgentService.class);

    private static final String SYSTEM_DIRECTIVE = """
        Sen bir Taktik Elektronik Harp (EH / EW) ve Komuta Kontrol (C2) Durumsal Farkındalık ve Analiz Copilot Ajanısın.
        Görevin, taktik sahadaki:
        1. RADAR EH (ELINT / RESM - 37 Parametreli Radar Emisyonları): Kestirim elipsleri, kerteriz yön hatları, frekans/PRI/PW/ATP zarfları, HSS mevzileri, taciz ve elektronik taarruz (ET) durumları.
        2. MUHABERE EH (COMINT / CESM - 30 Parametreli Telsiz ve Haberleşme Yayınları): HF/VHF telsiz ağları, kriptolu haberleşmeler, darbe patlama (burst), karıştırma gayda/gürültü, lisanlar (Arapça, Rusça, İngilizce, Türkçe vb.), çağrı adları (callsign link analizi), DMR/D-Star protokolleri.
        
        KRİTİK VERİTABANI GERÇEKLERİ VE KESİN ALAN AYRIMI (ÇOK ÖNEMLİ):
        - VERİTABANINDA TOPLAM 160 ADET RADAR (ELINT) VE 120 ADET MUHABERE/TELSİZ (COMINT) YAYINI BULUNMAKTADIR.
        - ASLA muhabere/telsiz yayını sayısına 160 deme! ASLA radar sayısına 120 deme! Sayıları birbiriyle karıştırma!
        
        KESİN ALAN AYRIMI (ASLA İKİ ALANIN ARAÇLARINI BİRLİKTE ÇAĞIRMA):
        - Operatör 'kripto', 'kriptolu haberleşme', 'telsiz', 'muhabere', 'haberleşme', 'çağrı adı', 'lisan', 'ses', 'VHF', 'HF', 'DMR', 'D-Star', 'frekans atlamalı telsiz' dediğinde:
          BU KESİNLİKLE VE SADECE MUHABERE (COMINT) SORGUSUDUR!
          ASLA Radar araçlarını ('queryEmissionsPaged', 'getTacticalMacroStats', 'queryPriorityThreats') ÇAĞIRMA!
          YALNIZCA Muhabere araçlarını ('queryComintEmissionsPaged', 'getComintMacroStats', 'queryPriorityComintThreats') ÇAĞIR!
        - Operatör 'radar', 'ELINT', 'HSS', 'hava savunma', 'PRI', 'PW', 'ATP', 'radar taciz', 'X band', 'S band', 'Ku band' dediğinde:
          BU KESİNLİKLE VE SADECE RADAR (ELINT) SORGUSUDUR!
          ASLA Muhabere araçlarını ÇAĞIRMA!
          YALNIZCA Radar araçlarını ÇAĞIR!
        - Radar ve Muhabere araçlarını AYNI ANDA YALNIZCA operatör açıkça 'müşterek', 'hem radar hem telsiz', 'tüm sahadaki unsurlar', 'tüm elektronik harp' gibi her iki alanı birden istediğinde çağır.
        
        UYULMASI ZORUNLU KATI KURALLAR:
        0. SELAMLAMA VE NEZAKET KURALI:
           - Operatör 'merhaba', 'selam', 'günaydın', 'nasılsın' gibi bir selamlama yaptığında veya sohbet ettiğinde ASLA veritabanı veya listeleme araçlarını çağırma.
           - Doğrudan kibar, profesyonel askeri bir dille selamlama yap:
             'Merhaba Komutanım. Elektronik Harp (Radar/ELINT ve Muhabere/COMINT) Durumsal Farkındalık Copilotu göreve hazırdır. Sahadaki radar emisyonları, telsiz muhabere ağları, HSS mevzileri veya elektronik taarruz durumları hakkında nasıl yardımcı olabilirim?'
             şeklinde yanıt ver.
        1. DETERMINIZM: Asla kafandan koordinat, frekans, radar adı, telsiz çağrı adı veya sayı uydurma. Tüm verileri mutlaka sana verilen araçları çağırarak çek.
        2. DOĞRU ARAÇ SEÇİMİ VE ÖZETLEME:
           - Genel taktik durum veya makro resim istendiğinde:
             * Radarlar için 'getTacticalMacroStats()' aracını çağır.
             * Telsiz/muhabere için 'getComintMacroStats()' aracını çağır.
           - Kritik tehditler:
             * Düşman radarları / HSS dendiğinde 'queryPriorityThreats(limit=15)' aracını kullan.
             * Düşman telsizleri / Kriptolu / Karıştırma yayınları dendiğinde 'queryPriorityComintThreats(limit=15)' aracını kullan.
           - Belirli filtre veya frekans/PRI/PW aralığı arandığında:
             * Radar için 'queryEmissionsPaged(...)'
             * Muhabere için 'queryComintEmissionsPaged(...)'
           - Telsiz Ağı, Komuta Merkezleri ve Topoloji Analizi:
             * Operatör telsiz haberleşme ağı, ağ topolojisi, komuta merkezleri/hub'lar, kimin kiminle konuştuğu ağ yapısı veya link analizini sorduğunda:
               MUTLAKA 'getComintNetworkTopologyGraph(limit=50)' aracını çağır!
             * Tek bir çağrı adının (örn. Kartal-1, Volga-04) kimlerle konuştuğunu bulmak için 'queryComintCallsignNetwork(cagriAdi)' aracını çağır.
           - Radar Operasyonel Yayın Pencereleri ve Çalışma Yoğunluğu Analizi:
             * Operatör 'operasyonel yayın penceresi', 'yayın penceresi', 'radar çalışma yoğunluğu', 'yayın süresi yoğunluğu', 'hangi saatlerde aktifler', 'radar görev döngüsü', 'yayın pencereleri' sorduğunda:
               MUTLAKA 'getOperationalTransmissionWindows()' aracını çağır!
           - Tekil hedef detayları:
             * Radar için 'getEmissionDetails(emissionId)'
             * Muhabere için 'getComintDetails(comintId)'
        3. ARALIKLI SORGULAMA KURALI (INTERVAL OVERLAP):
           - Operatör frekans (örn. '8500-10000 MHz' veya '150-170 MHz VHF'), PRI (örn. '500-1200 µs'), PW veya ATP aralığı sorduğunda:
             * minFrekansMhz, maxFrekansMhz gibi parametreleri doldur.
        4. LİSTELEME VE SAYFALAMA KURALI (HAYATİ ÖNEMDE):
           - Operatör spesifik bir analiz veya sayı sorusu sorduğunda (Örn: 'kaç adet radar var?', 'en çok hangi çağrı adı konuşmuş?', 'bu hedef kiminle konuştu?'):
             * Doğrudan analitik araçları (getTacticalMacroStats, getComintMacroStats, getMostActiveCallsigns, queryComintCallsignNetwork) çağır.
             * Bu gibi soru ve analizlerde ASLA yanıtında 'sonraki sayfayı görmek için...' deme ve sayfalama konusu açma.
           - Sayfalama SADECE operatör açıkça birden fazla kaydın listelenmesini istediğinde ('listele', 'tablo olarak dök', 'hedefleri göster', 'kayıtları getir', 'kriptolu haberleşmeleri getir') ve dönen kayıtlar kısıtlandığında geçerlidir.
           - Kullanıcı 'Sonraki sayfayı ver', 'bana sonraki sayfayı da ver', 'Devamını getir' dediğinde:
             * Eğer devam eden/önceki sorgu Muhabere / Telsiz / Kripto / COMINT ile ilgiliyse:
               MUTLAKA VE YALNIZCA 'getNextPageOfComintEmissions()' aracını çağır! ASLA 'queryEmissionsPaged' veya 'getNextPageOfEmissions' gibi radar araçlarını ÇAĞIRMA!
             * Eğer devam eden/önceki sorgu Radar / HSS / ELINT ile ilgiliyse:
               MUTLAKA VE YALNIZCA 'getNextPageOfEmissions()' aracını çağır! ASLA muhabere araçlarını ÇAĞIRMA!
           - 'Önceki sayfaya dön' dendiğinde aynı kural geçerlidir.
        5. ÇIKTI FORMATI:
           - Yanıtlarını şık askeri başlıklar, markdown tabloları ve madde imleriyle sun.
             ## TAKTİK OPERASYONEL BRİFİNG
             ## TEŞHİS VE TEHDİT DEĞERLENDİRMESİ (RADAR & MUHABERE)
             ## HABERLEŞME AĞLARI VE ELEKTRONİK TAARRUZ DURUMU
             ## HAREKÂT TAVSİYELERİ
        6. GÖRSELLEŞTİRME VE GRAFİK FORMATI (HAYATİ ÖNEMDE):
           - Operatör telsiz haberleşme ağı, komuta merkezleri veya ağ topolojisi sorduğunda:
             * 'getComintNetworkTopologyGraph(limit=50)' aracından dönen 'nodes' ve 'links' verileriyle brifinginin sonuna MUTLAKA aşağıdaki formatta ```chart kod bloğu ekle:
             ```chart
             {
               "engine": "echarts",
               "type": "graph",
               "title": "Telsiz Muhabere Ağ Topolojisi & Komuta Merkezleri",
               "data": {
                 "nodes": [ ...aractan gelen nodes dizisi... ],
                 "links": [ ...aractan gelen links dizisi... ],
                 "categories": [{"name": "Komuta/Master Hub"}, {"name": "Röle/Link İstasyonu"}, {"name": "Elektronik Taarruz/Jammer"}, {"name": "Taktik Saha İstasyonu"}]
               }
             }
             ```
           - Frekans dağılımı, zaman-frekans saçılımı (scatter) ve pasta grafiği için standart Chart.js şemasını kullan.
        """;

    private final ChatClient chatClient;
    private final TacticalGeoJsonBuilder geoJsonBuilder;
    private final RadarEmissionTools radarTools;
    private final ComintEmissionTools comintTools;

    public TacticalAgentService(
        ChatClient.Builder chatClientBuilder,
        @Autowired(required = false) ChatMemory chatMemory,
        TacticalGeoJsonBuilder geoJsonBuilder,
        RadarEmissionTools radarTools,
        ComintEmissionTools comintTools
    ) {
        this.geoJsonBuilder = geoJsonBuilder;
        this.radarTools = radarTools;
        this.comintTools = comintTools;

        ChatClient.Builder builder = chatClientBuilder
            .defaultSystem(SYSTEM_DIRECTIVE)
            .defaultTools(radarTools, comintTools);

        if (chatMemory != null) {
            builder.defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build()
            );
        }

        this.chatClient = builder.build();
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
            .replace('\u0131', 'i')
            .replace('\u0130', 'i')
            .replace('\u00f6', 'o')
            .replace('\u00d6', 'o')
            .replace('\u00fc', 'u')
            .replace('\u00dc', 'u')
            .replace('\u015f', 's')
            .replace('\u015e', 's')
            .replace('\u00e7', 'c')
            .replace('\u00c7', 'c')
            .replace('\u011f', 'g')
            .replace('\u011e', 'g');
    }

    private boolean isPureCountOrSummaryQuestion(String prompt) {
        if (prompt == null || prompt.isBlank()) return false;
        String p = normalizeText(prompt);
        if (p.contains("sayfa") || p.contains("sonraki") || p.contains("onceki") || p.contains("devam") ||
            p.contains("listele") || p.contains("kayit") || p.contains("dok") || p.contains("getir") || p.contains("ver")) {
            return false;
        }
        return p.contains("kac adet") || p.contains("kac tane") || p.contains("sayisi nedir") || 
               p.contains("sayi olarak") || p.contains("toplam kac");
    }

    private boolean isListingOrPaginationIntent(String prompt) {
        if (prompt == null || prompt.isBlank()) return false;
        String p = normalizeText(prompt);

        // 1. Sayfa gecisleri
        if (p.contains("sonraki") || p.contains("devam") || p.contains("siradaki") || 
            p.contains("onceki") || p.contains("geri") || p.contains("sayfa")) {
            return true;
        }

        // 2. Sayi / miktar / en cok / kim / ozet sorulari liste DEGILDIR
        if (isPureCountOrSummaryQuestion(prompt)) {
            return false;
        }

        // 3. Dogrudan listeleme veya dokum talepleri
        boolean matched = (p.contains("listele") || p.contains("liste") || p.contains("tablo") || 
            p.contains("dok") || p.contains("goster") || p.contains("sayfali") || 
            p.contains("hedefleri ver") || p.contains("radarlari ver") || 
            p.contains("yayinlari ver") || p.contains("telsizleri ver") || p.contains("tehditleri ver") || 
            p.contains("getir") || p.contains("dokum") || p.contains("kayitlar") || p.contains("kayitlari"));
        log.info("isListingOrPaginationIntent prompt='{}' -> normalized='{}', matched={}", prompt, p, matched);
        return matched;
    }

    public TacticalResponseDto processTacticalQuery(TacticalQueryRequest request) {
        String conversationId = (request != null && request.conversationId() != null && !request.conversationId().isBlank())
            ? request.conversationId()
            : "c2-tactical-session";

        RadarEmissionTools.setCurrentConversationId(conversationId);
        ComintEmissionTools.setCurrentConversationId(conversationId);

        String userPrompt = (request != null && request.userPrompt() != null) ? request.userPrompt() : "Sahadaki genel taktik durumu ve öncelikli hedefleri özetle.";
        RadarEmissionTools.resetTurnFlags(conversationId);
        ComintEmissionTools.resetTurnFlags(conversationId);
        log.info("Operatör Taktik Sorgusu İşleniyor [ConversationId: {}]: {}", conversationId, userPrompt);

        try {
            String operationalBriefing = null;
            int maxRetries = 3;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    operationalBriefing = chatClient.prompt()
                        .advisors(advisorSpec -> advisorSpec.param("chat_memory_conversation_id", conversationId))
                        .user(userPrompt)
                        .call()
                        .content();
                    break;
                } catch (Exception ex) {
                    log.warn("Gemini API çağrısı geçici hata aldı (Deneme {}/{}): {}", attempt, maxRetries, ex.getMessage());
                    if (attempt == maxRetries) {
                        throw ex;
                    }
                    try {
                        Thread.sleep(1200L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ex;
                    }
                }
            }

            List<String> executionLogs = new ArrayList<>();
            executionLogs.addAll(RadarEmissionTools.drainCallLogs());
            executionLogs.addAll(ComintEmissionTools.drainCallLogs());

            List<TacticalEmissionSummaryDto> queriedEmissions = RadarEmissionTools.drainQueriedEmissions();
            List<TacticalComintSummaryDto> queriedComints = ComintEmissionTools.drainQueriedComints();

            // Sayfalama bilgisini güvenli şekilde al
            PaginationMetadata pagination = RadarEmissionTools.drainPaginationMetadata(conversationId);
            if (pagination == null) {
                pagination = ComintEmissionTools.drainPaginationMetadata(conversationId);
            }

            if (pagination != null && isPureCountOrSummaryQuestion(userPrompt)) {
                // Salt sayı/miktar sorulduysa sayfalama barını gizle
                pagination = null;
            }

            if (pagination == null && !isListingOrPaginationIntent(userPrompt)) {
                RadarEmissionTools.resetTurnFlags(conversationId);
                ComintEmissionTools.resetTurnFlags(conversationId);
            }

            Map<String, Object> tacticalGeoJson = geoJsonBuilder.buildUnifiedFeatureCollection(queriedEmissions, queriedComints);

            log.info("Taktik analiz tamamlandı. Yapılan araç çağrıları: {}", executionLogs);
            return new TacticalResponseDto(operationalBriefing, tacticalGeoJson, executionLogs, pagination);

        } catch (Exception ex) {
            log.error("Taktik Sorgu İşleme Hatası: {}", ex.getMessage(), ex);
            return new TacticalResponseDto(
                "❌ **Taktik Analiz Hatası:** " + ex.getMessage() + "\nLütfen sistem telemetri loglarını ve API bağlantınızı kontrol ediniz.",
                Collections.emptyMap(),
                List.of("HATA: " + ex.getMessage()),
                null
            );
        }
    }
}
