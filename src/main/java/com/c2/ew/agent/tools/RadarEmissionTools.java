package com.c2.ew.agent.tools;

import com.c2.ew.agent.dto.PaginationMetadata;
import com.c2.ew.domain.model.EmitterActivity;
import com.c2.ew.domain.model.EmitterFix;
import com.c2.ew.domain.model.EmitterLob;
import com.c2.ew.domain.model.PagedResult;
import com.c2.ew.infrastructure.repository.TacticalEmissionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Spring AI Taktik Elektronik Harp Araç Kataloğu (Tool Specification)
 * Büyük Veri, SQL Agregasyonları ve Akıllı Sayfa Geçişi (Next-Page) Desteği
 */
@Component
public class RadarEmissionTools {

    private static final Logger log = LoggerFactory.getLogger(RadarEmissionTools.class);

    private static final ThreadLocal<List<String>> CALL_LOGS = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<Map<String, EmitterFix>> QUERIED_FIXES = ThreadLocal.withInitial(LinkedHashMap::new);
    private static final ThreadLocal<Map<String, EmitterLob>> QUERIED_LOBS = ThreadLocal.withInitial(LinkedHashMap::new);

    // Oturum Bazlı Sayfalama Hafızası (Session Pagination State - Thread & Request Safe)
    private static final Map<String, ActivePaginationState> SESSION_STATES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final ThreadLocal<String> CURRENT_CONVERSATION_ID = ThreadLocal.withInitial(() -> "c2-tactical-session");

    public static void setCurrentConversationId(String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            CURRENT_CONVERSATION_ID.set(conversationId);
        }
    }

    public static String getCurrentConversationId() {
        return CURRENT_CONVERSATION_ID.get();
    }

    public static ActivePaginationState getActiveState() {
        return SESSION_STATES.computeIfAbsent(CURRENT_CONVERSATION_ID.get(), k -> new ActivePaginationState());
    }

    public static class ActivePaginationState {
        public String lastBand;
        public EmitterActivity lastStatus;
        public Integer lastMinThreat;
        public String lastRadarType;
        public String lastPlatformType;
        public int lastFixPage = -1;
        public int fixPageSize = 15;
        public long fixTotalCount = 0;
        public int fixTotalPages = 0;
        public boolean fixHasMore = false;

        public String lastLobSensor;
        public String lastLobBand;
        public String lastLobAssociation = "ALL";
        public boolean lastLobUnassociated = false;
        public int lastLobPage = -1;
        public int lobPageSize = 20;
        public long lobTotalCount = 0;
        public int lobTotalPages = 0;
        public boolean lobHasMore = false;

        public String lastTargetType; // "FIX" or "LOB"
    }

    private final TacticalEmissionRepository repository;

    public RadarEmissionTools(TacticalEmissionRepository repository) {
        this.repository = repository;
    }

    public static void clearSession() {
        CALL_LOGS.get().clear();
        QUERIED_FIXES.get().clear();
        QUERIED_LOBS.get().clear();
    }

    public static List<String> getCapturedLogs() {
        return new ArrayList<>(CALL_LOGS.get());
    }

    public static Map<String, EmitterFix> getCapturedFixes() {
        return new LinkedHashMap<>(QUERIED_FIXES.get());
    }

    public static Map<String, EmitterLob> getCapturedLobs() {
        return new LinkedHashMap<>(QUERIED_LOBS.get());
    }

    public static PaginationMetadata getLastPaginationMetadata() {
        ActivePaginationState state = getActiveState();
        if ("FIX".equals(state.lastTargetType) && state.fixHasMore) {
            String filterDesc = (state.lastPlatformType != null ? state.lastPlatformType + " " : "") +
                                (state.lastRadarType != null ? state.lastRadarType + " " : "") +
                                (state.lastBand != null ? state.lastBand + "-Band " : "") +
                                (state.lastStatus != null ? state.lastStatus.name() + " " : "") + "radarlarının";
            String prompt = String.format("%s %d. sayfasını (Sayfa %d/%d) listele.",
                filterDesc.isBlank() ? "Önceki hedeflerin" : filterDesc,
                state.lastFixPage + 2, state.lastFixPage + 2, state.fixTotalPages);

            return new PaginationMetadata(true, state.lastFixPage, state.fixTotalPages, state.fixTotalCount, state.fixPageSize, "FIX", prompt);
        } else if ("LOB".equals(state.lastTargetType) && state.lobHasMore) {
            String assocDesc = "";
            if (state.lastLobAssociation != null) {
                String norm = state.lastLobAssociation.toUpperCase();
                if (norm.contains("UNASSOC") || norm.contains("YETIM") || norm.contains("ORPHAN")) {
                    assocDesc = "yetim (ilişkilendirilmemiş) ";
                } else if (norm.contains("ASSOC") || norm.contains("ILISKI") || norm.contains("İLİŞKİ")) {
                    assocDesc = "kestirimlerle ilişkilendirilmiş ";
                }
            }
            String filterDesc = assocDesc +
                                (state.lastLobSensor != null ? state.lastLobSensor + " sensörünün " : "") +
                                (state.lastLobBand != null ? state.lastLobBand + "-Band " : "") + "LOB sinyallerinin";
            String prompt = String.format("%s %d. sayfasını (Sayfa %d/%d) listele.",
                filterDesc.isBlank() ? "Önceki LOB sinyallerinin" : filterDesc,
                state.lastLobPage + 2, state.lastLobPage + 2, state.lobTotalPages);

            return new PaginationMetadata(true, state.lastLobPage, state.lobTotalPages, state.lobTotalCount, state.lobPageSize, "LOB", prompt);
        }
        return null;
    }

    private void logTrace(String entry) {
        log.info(entry);
        CALL_LOGS.get().add(entry);
    }

    @Tool(description = "Taktik harekât sahasındaki tüm yayıncıların ve LOB hatlarının makro istatistiklerini döner: Toplam kestirim sayısı, aktif/suskun/kesikli yayın sayıları, bant dağılımı (fixesByBand), tehdit seviyesi dağılımı, toplam ve sensör bazlı LOB sayıları. Genel durum, özet veya grafik çizimi soruları için EN BİRİNCİL araçtır.")
    public Map<String, Object> getTacticalSummaryStats() {
        logTrace("🔍 [GEMINI -> TOOL CALL] getTacticalSummaryStats()");
        Map<String, Object> stats = repository.getOperationalStatistics();
        logTrace(String.format("⚡ [TOOL RESULT -> GEMINI] Makro İstatistikler: Toplam Fix=%s, Aktif=%s, Toplam LOB=%s",
            stats.get("totalFixes"), stats.get("activeFixes"), stats.get("totalLobs")));
        return stats;
    }

    @Tool(description = "En kritik taktik tehditleri (tehdit seviyesi 1-5 ve son görülme zamanına göre önceliklendirilmiş) listeler. LLM bağlamı ve token tasarrufu için varsayılan olarak en kritik ilk 15 (azami 30) hedefi çeker. 'Kritik radarları listele', 'En tehlikeli hedefler neler?', 'Öncelikli tehditler' soruları için kullanılır.")
    public List<EmitterFix> queryPriorityEmitters(Integer limit) {
        int safeLimit = (limit != null && limit > 0) ? Math.min(30, limit) : 15;
        logTrace(String.format("🔍 [GEMINI -> TOOL CALL] queryPriorityEmitters(limit=%d)", safeLimit));

        List<EmitterFix> results = repository.findTopThreats(safeLimit);
        results.forEach(f -> QUERIED_FIXES.get().put(f.fixId(), f));

        String summary = results.stream()
            .map(f -> String.format("%s [%s / %s] (%s, %.0fMHz)", f.fixId(), f.radarType(), f.platformType(), f.rfSignature().band(), f.rfSignature().frequencyMhz()))
            .collect(Collectors.joining("; "));

        logTrace(String.format("⚡ [TOOL RESULT -> GEMINI] %d adet öncelikli hedef iletildi: [%s]", results.size(), summary));
        return results;
    }

    public static String cleanParam(String val) {
        if (val == null || val.isBlank()) return null;
        String trimmed = val.trim();
        if (trimmed.equals("*") || trimmed.equalsIgnoreCase("ALL") || 
            trimmed.equalsIgnoreCase("ANY") || trimmed.equalsIgnoreCase("NULL") || 
            trimmed.equalsIgnoreCase("NONE") || trimmed.equalsIgnoreCase("TÜMÜ") || 
            trimmed.equalsIgnoreCase("HEPSİ") || trimmed.equalsIgnoreCase("TÜM")) {
            return null;
        }
        return trimmed;
    }

    @Tool(description = "Kestirilmiş radar elipslerini (EmitterFix) sayfalı (PagedResult) olarak listeler. " +
        "Filtreler: " +
        "- page: 0-tabanlı sayfa numarası. " +
        "- pageSize: Sayfa başı hedef (varsayılan 15, azami 30). " +
        "- status: ACTIVE, INTERMITTENT, SILENT (veya null). " +
        "- band: Frekans bandı ('X', 'S', 'C', 'Ku', 'L'). Kullanıcı açıkça bir bant belirtmedikçe null bırakılmalıdır (asla varsayım yapma veya tek bir bant uydurma). " +
        "- minThreatLevel: Minimum tehdit seviyesi (1-5). " +
        "- radarType: Belirli bir radar modeli/adı (örn: 'S-400', 'S-300', 'Sentinel', 'F-16', 'P-18', 'Flycatcher'). Kullanıcı açıkça bir model sormadıkça null bırakılmalıdır (asla '*' verme). " +
        "- platformType: Platform türü ('AIRBORNE', 'NAVAL', 'LAND_MOBILE', 'FIXED_SITE'). " +
        "Kullanıcı 'havadaki radarlar' derse platformType='AIRBORNE', 'deniz hedefleri' derse platformType='NAVAL', 'S-400 var mı' derse radarType='S-400' verilmelidir.")
    public PagedResult<EmitterFix> queryEmitterFixesPaged(
        Integer page,
        Integer pageSize,
        EmitterActivity status,
        String band,
        Integer minThreatLevel,
        String radarType,
        String platformType
    ) {
        int p = (page != null && page >= 0) ? page : 0;
        int size = (pageSize != null && pageSize > 0) ? Math.min(30, pageSize) : 15;

        String cleanBand = cleanParam(band);
        String cleanRadar = cleanParam(radarType);
        String cleanPlatform = cleanParam(platformType);

        logTrace(String.format("🔍 [GEMINI -> TOOL CALL] queryEmitterFixesPaged(page=%d, size=%d, status=%s, band=%s, minThreat=%s, radar=%s, platform=%s)",
            p, size, status, cleanBand, minThreatLevel, cleanRadar, cleanPlatform));

        PagedResult<EmitterFix> paged = repository.findFixesPaged(p, size, status, cleanBand, minThreatLevel, cleanRadar, cleanPlatform);
        paged.items().forEach(f -> QUERIED_FIXES.get().put(f.fixId(), f));

        // Sayfalama durumunu oturum hafızasına kaydet
        ActivePaginationState state = getActiveState();
        state.lastTargetType = "FIX";
        state.lastBand = cleanBand;
        state.lastStatus = status;
        state.lastMinThreat = minThreatLevel;
        state.lastRadarType = cleanRadar;
        state.lastPlatformType = cleanPlatform;
        state.lastFixPage = p;
        state.fixPageSize = size;
        state.fixTotalCount = paged.totalCount();
        state.fixTotalPages = paged.totalPages();
        state.fixHasMore = paged.hasMore();

        logTrace(String.format("⚡ [TOOL RESULT -> GEMINI] Sayfa %d/%d getirildi. Toplam kayıt: %d, Sonraki Sayfa Var mı?: %s",
            paged.page() + 1, paged.totalPages(), paged.totalCount(), paged.hasMore()));
        return paged;
    }

    @Tool(description = "Önceki radar kestirim sorgusunun (EmitterFix) bir sonraki sayfasını çeker. Kullanıcı 'Sonraki sayfayı ver', 'Devamını göster', 'Bir sonraki sayfayı getir', 'Sıradaki hedefler' dediğinde bu aracı çağır. Önceki filtre kriterlerini (bant, durum vb.) otomatik hatırlar ve bir sonraki sayfayı (page + 1) döner.")
    public PagedResult<EmitterFix> getNextPageOfFixes() {
        ActivePaginationState state = getActiveState();
        int nextPage = state.lastFixPage + 1;
        int size = state.fixPageSize > 0 ? state.fixPageSize : 15;

        logTrace(String.format("🔍 [GEMINI -> TOOL CALL] getNextPageOfFixes() -> Sayfa %d çağrılıyor (Filtreler: band=%s, status=%s, radar=%s, platform=%s)",
            nextPage, state.lastBand, state.lastStatus, state.lastRadarType, state.lastPlatformType));

        return queryEmitterFixesPaged(nextPage, size, state.lastStatus, state.lastBand, state.lastMinThreat, state.lastRadarType, state.lastPlatformType);
    }

    @Tool(description = "Önceki radar kestirim sorgusunun (EmitterFix) bir önceki sayfasını çeker. Kullanıcı 'Önceki sayfaya dön', 'Geri git', 'Bir önceki sayfayı göster' dediğinde bu aracı çağır.")
    public PagedResult<EmitterFix> getPrevPageOfFixes() {
        ActivePaginationState state = getActiveState();
        int prevPage = Math.max(0, state.lastFixPage - 1);
        int size = state.fixPageSize > 0 ? state.fixPageSize : 15;

        logTrace(String.format("🔍 [GEMINI -> TOOL CALL] getPrevPageOfFixes() -> Sayfa %d çağrılıyor (Filtreler: band=%s, status=%s, radar=%s, platform=%s)",
            prevPage, state.lastBand, state.lastStatus, state.lastRadarType, state.lastPlatformType));

        return queryEmitterFixesPaged(prevPage, size, state.lastStatus, state.lastBand, state.lastMinThreat, state.lastRadarType, state.lastPlatformType);
    }

    @Tool(description = "Zaman - Frekans (Time-Frequency Scatter / Waterfall) radar çalışma ve aktivite yoğunluğu verisini döner. " +
        "Operatör 'zaman frekans grafiği', 'çalışma yoğunluğu grafiği', 'waterfall', 'spektrogram', 'frekans zaman dağılımı' " +
        "istediğinde KESİNLİKLE bu aracı çağır! " +
        "Dönen her bir nokta {timeFormatted (HH:mm), frequencyMhz, radarType, platformType, band, threatLevel} içerir. " +
        "Filtreler: lastHours (varsayılan 24), band (Ku, X, S, C, L veya null), platformType (AIRBORNE, NAVAL, LAND_MOBILE vb.), radarType.")
    public Map<String, Object> getTimeFrequencyScatterData(
        Integer lastHours,
        String band,
        String platformType,
        String radarType
    ) {
        String cleanBand = cleanParam(band);
        String cleanPlatform = cleanParam(platformType);
        String cleanRadar = cleanParam(radarType);

        logTrace(String.format("🔍 [GEMINI -> TOOL CALL] getTimeFrequencyScatterData(hours=%s, band=%s, platform=%s, radar=%s)",
            lastHours, cleanBand, cleanPlatform, cleanRadar));

        List<Map<String, Object>> points = repository.getTimeFrequencyPoints(lastHours, cleanBand, cleanPlatform, cleanRadar);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("totalPoints", points.size());
        result.put("dataPoints", points);

        logTrace(String.format("⚡ [TOOL RESULT -> GEMINI] Zaman-Frekans Saçılım Verisi: %d adet nokta iletildi.", points.size()));
        return result;
    }

    @Tool(description = "Ham LOB yön kestirim sinyallerinin kesin toplam sayısını ve sensör/bant dağılımını döner. associationStatus: 'ALL' (hepsi), 'UNASSOCIATED' (yetim LOB'lar), 'ASSOCIATED' (kestirimlerle ilişkilendirilmiş LOB'lar). Belirli bir sensör veya bant kısıtı yoksa sensorNodeId ve band parametreleri null bırakılmalıdır (asla 'ALL' verilmemeli).")
    public Map<String, Object> countEmitterLobs(
        Integer lastMinutes,
        String associationStatus,
        String sensorNodeId,
        String band,
        Double minFrequencyMhz,
        Double maxFrequencyMhz
    ) {
        String cleanSensor = cleanParam(sensorNodeId);
        String cleanBand = cleanParam(band);
        String cleanStatus = cleanParam(associationStatus);

        logTrace(String.format("🔍 [GEMINI -> TOOL CALL] countEmitterLobs(associationStatus=%s, sensor=%s, band=%s)", cleanStatus, cleanSensor, cleanBand));
        Map<String, Object> result = repository.countLobs(lastMinutes, cleanStatus, cleanSensor, cleanBand, minFrequencyMhz, maxFrequencyMhz);
        logTrace(String.format("⚡ [TOOL RESULT -> GEMINI] Toplam LOB: %s, İlişki Durumu: %s", result.get("totalLobs"), cleanStatus != null ? cleanStatus : "ALL"));
        return result;
    }

    @Tool(description = "Ham LOB (Line of Bearing) yön kestirim sinyallerini sayfalı olarak listeler. associationStatus parametresi: 'ASSOCIATED' (yalnızca kestirimlerle ilişkilendirilmiş olanlar), 'UNASSOCIATED' (yalnızca yetim/ilişkilendirilmemiş açıkta kalan hatlar), veya 'ALL' (hepsi). Kullanıcı 'ilişkilendirilmiş' dediğinde KESİNLİKLE 'ASSOCIATED' seçilmelidir! 'Yetim / ilişkilendirilmemiş' dediğinde 'UNASSOCIATED' seçilmelidir. Kullanıcı belirli bir sensör belirtmedikçe sensorNodeId parametresi KESİNLİKLE null bırakılmalıdır (asla 'ALL' stringi verme). Parametreler: page (0-tabanlı), pageSize (varsayılan 20, azami 50), associationStatus ('ALL', 'UNASSOCIATED', 'ASSOCIATED'), sensorNodeId, band.")
    public PagedResult<EmitterLob> queryEmitterLobsPaged(
        Integer page,
        Integer pageSize,
        String associationStatus,
        String sensorNodeId,
        String band
    ) {
        int p = (page != null && page >= 0) ? page : 0;
        int size = (pageSize != null && pageSize > 0) ? Math.min(50, pageSize) : 20;

        String cleanSensor = cleanParam(sensorNodeId);
        String cleanBand = cleanParam(band);
        String cleanStatus = cleanParam(associationStatus);

        logTrace(String.format("🔍 [GEMINI -> TOOL CALL] queryEmitterLobsPaged(page=%d, size=%d, associationStatus=%s, sensor=%s, band=%s)",
            p, size, cleanStatus != null ? cleanStatus : "ALL", cleanSensor, cleanBand));

        PagedResult<EmitterLob> paged = repository.findLobsPaged(p, size, cleanStatus, cleanSensor, cleanBand);
        paged.items().forEach(l -> QUERIED_LOBS.get().put(l.lobId(), l));

        ActivePaginationState state = getActiveState();
        state.lastTargetType = "LOB";
        state.lastLobAssociation = cleanStatus != null ? cleanStatus : "ALL";
        state.lastLobUnassociated = "UNASSOCIATED".equalsIgnoreCase(cleanStatus);
        state.lastLobSensor = cleanSensor;
        state.lastLobBand = cleanBand;
        state.lastLobPage = p;
        state.lobPageSize = size;
        state.lobTotalCount = paged.totalCount();
        state.lobTotalPages = paged.totalPages();
        state.lobHasMore = paged.hasMore();

        logTrace(String.format("⚡ [TOOL RESULT -> GEMINI] LOB Sayfa %d/%d (Toplam: %d, Sonraki Sayfa Var mı?: %s)",
            paged.page() + 1, paged.totalPages(), paged.totalCount(), paged.hasMore()));
        return paged;
    }

    @Tool(description = "Önceki LOB sinyali sorgusunun bir sonraki sayfasını çeker. Kullanıcı LOB sinyallerinin devamını veya bir sonraki sayfasını istediğinde bu aracı çağır.")
    public PagedResult<EmitterLob> getNextPageOfLobs() {
        ActivePaginationState state = getActiveState();
        int nextPage = state.lastLobPage + 1;
        int size = state.lobPageSize > 0 ? state.lobPageSize : 20;

        logTrace(String.format("🔍 [GEMINI -> TOOL CALL] getNextPageOfLobs() -> LOB Sayfa %d çağrılıyor (AssociationStatus=%s, Sensor=%s, Band=%s)",
            nextPage, state.lastLobAssociation, state.lastLobSensor, state.lastLobBand));

        return queryEmitterLobsPaged(nextPage, size, state.lastLobAssociation, state.lastLobSensor, state.lastLobBand);
    }

    @Tool(description = "Önceki LOB sinyali sorgusunun bir önceki sayfasını çeker. Kullanıcı LOB sinyallerinin bir önceki sayfasını istediğinde bu aracı çağır.")
    public PagedResult<EmitterLob> getPrevPageOfLobs() {
        ActivePaginationState state = getActiveState();
        int prevPage = Math.max(0, state.lastLobPage - 1);
        int size = state.lobPageSize > 0 ? state.lobPageSize : 20;

        logTrace(String.format("🔍 [GEMINI -> TOOL CALL] getPrevPageOfLobs() -> LOB Sayfa %d çağrılıyor (AssociationStatus=%s, Sensor=%s, Band=%s)",
            prevPage, state.lastLobAssociation, state.lastLobSensor, state.lastLobBand));

        return queryEmitterLobsPaged(prevPage, size, state.lastLobAssociation, state.lastLobSensor, state.lastLobBand);
    }

    @Tool(description = "Belirtilen kriterlere uyan radar kestirimlerini coğrafi veya teknik parametrelerle filtreler. Filtreler: centerLat, centerLon, radiusKm, lastMinutes, status, band, minFrequencyMhz, maxFrequencyMhz, maxSemiMajorAxisMeters.")
    public List<EmitterFix> queryEmitterFixes(
        Double centerLat,
        Double centerLon,
        Double radiusKm,
        Integer lastMinutes,
        EmitterActivity status,
        String band,
        Double minFrequencyMhz,
        Double maxFrequencyMhz,
        Double maxSemiMajorAxisMeters,
        Double minSemiMajorAxisMeters
    ) {
        logTrace(String.format("🔍 [GEMINI -> TOOL CALL] queryEmitterFixes(lat=%s, lon=%s, rad=%s, band=%s, status=%s)",
            centerLat, centerLon, radiusKm, band, status));

        List<EmitterFix> results = repository.findFixes(centerLat, centerLon, radiusKm, lastMinutes, status, band,
            minFrequencyMhz, maxFrequencyMhz, maxSemiMajorAxisMeters, minSemiMajorAxisMeters);
        
        List<EmitterFix> capped = results.stream().limit(25).toList();
        capped.forEach(f -> QUERIED_FIXES.get().put(f.fixId(), f));

        logTrace(String.format("⚡ [TOOL RESULT -> GEMINI] %d hedef bulundu, bağlama en kritik %d adet iletildi.",
            results.size(), capped.size()));
        return capped;
    }

    @Tool(description = "Belirli bir radar kestirimini (fixId) oluşturan kaynak LOB hatlarını döner.")
    public List<EmitterLob> getContributingLobsForFix(String fixId) {
        logTrace(String.format("🔍 [GEMINI -> TOOL CALL] getContributingLobsForFix(fixId=%s)", fixId));
        List<EmitterLob> lobs = repository.findContributingLobsForFix(fixId);
        lobs.forEach(l -> QUERIED_LOBS.get().put(l.lobId(), l));
        return lobs;
    }
}
