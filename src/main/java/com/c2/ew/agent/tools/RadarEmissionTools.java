package com.c2.ew.agent.tools;

import com.c2.ew.agent.dto.PaginationMetadata;
import com.c2.ew.agent.dto.TacticalEmissionDetailDto;
import com.c2.ew.agent.dto.TacticalEmissionSummaryDto;
import com.c2.ew.agent.dto.TacticalComintSummaryDto;
import com.c2.ew.domain.enums.*;
import com.c2.ew.domain.model.AtpEntry;
import com.c2.ew.domain.model.FrequencyEntry;
import com.c2.ew.domain.model.PagedResult;
import com.c2.ew.domain.model.PriEntry;
import com.c2.ew.domain.model.PwEntry;
import com.c2.ew.infrastructure.entity.TacticalEmissionEntity;
import com.c2.ew.infrastructure.repository.TacticalEmissionJpaRepository;
import com.c2.ew.infrastructure.entity.TacticalComintEntity;
import com.c2.ew.infrastructure.repository.TacticalComintJpaRepository;
import com.c2.ew.util.GeoUtils;
import com.c2.ew.domain.model.GeoPoint;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class RadarEmissionTools {

    private static final Logger log = LoggerFactory.getLogger(RadarEmissionTools.class);

    private static final ThreadLocal<List<String>> CALL_LOGS = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<Map<String, TacticalEmissionSummaryDto>> QUERIED_EMISSIONS = ThreadLocal.withInitial(LinkedHashMap::new);
    // state-based pagedToolCalledInTurn

        public static void putQueriedEmission(TacticalEmissionSummaryDto dto) {
        if (dto != null) {
            QUERIED_EMISSIONS.get().put(dto.id(), dto);
        }
    }

    public static void resetTurnFlags() {
        getActiveState().pagedToolCalledInTurn = false;
    }

    public static void resetTurnFlags(String convId) {
        if (convId != null && !convId.isBlank()) {
            SESSION_STATES.computeIfAbsent(convId, k -> new ActivePaginationState()).pagedToolCalledInTurn = false;
        }
    }

    // Oturum Bazlı Sayfalama Hafızası (Session Pagination State)
        private static final Map<String, ActivePaginationState> SESSION_STATES = new ConcurrentHashMap<>();
    private static volatile String LAST_CONVERSATION_ID = "c2-tactical-session";
    private static final ThreadLocal<String> CURRENT_CONVERSATION_ID = ThreadLocal.withInitial(() -> "c2-tactical-session");

    public static void setCurrentConversationId(String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            CURRENT_CONVERSATION_ID.set(conversationId);
            LAST_CONVERSATION_ID = conversationId;
        }
    }

    public static String getCurrentConversationId() {
        String id = CURRENT_CONVERSATION_ID.get();
        if (id == null || "c2-tactical-session".equals(id)) {
            return LAST_CONVERSATION_ID;
        }
        return id;
    }

    public static ActivePaginationState getActiveState() {
        return SESSION_STATES.computeIfAbsent(getCurrentConversationId(), k -> new ActivePaginationState());
    }

    public static ActivePaginationState getActiveState(String convId) {
        String id = (convId != null && !convId.isBlank()) ? convId : getCurrentConversationId();
        return SESSION_STATES.computeIfAbsent(id, k -> new ActivePaginationState());
    }

        public static class ActivePaginationState {
        public volatile boolean pagedToolCalledInTurn = false;
        public String lastTeshis;
        public String lastVeriKaynagi;
        public String lastRadarAdi;
        public String lastHss;
        public Boolean lastTaciz;
        public String lastEtUygulamaDurumu;
        public String lastPlatformOrtami;
        public Double lastMinFrekansMhz;
        public Double lastMaxFrekansMhz;
        public String lastFrekansTipi;
        public Double lastMinPriMicroSec;
        public Double lastMaxPriMicroSec;
        public Double lastMinPwMicroSec;
        public Double lastMaxPwMicroSec;
        public Double lastMinAtpMicroSec;
        public Double lastMaxAtpMicroSec;

        public int lastEmissionPage = -1;
        public int emissionPageSize = 15;
        public long emissionTotalCount = 0;
        public int emissionTotalPages = 0;
        public boolean emissionHasMore = false;
    }

    public static List<String> drainCallLogs() {
        List<String> logs = new ArrayList<>(CALL_LOGS.get());
        CALL_LOGS.get().clear();
        return logs;
    }

    public static List<TacticalEmissionSummaryDto> drainQueriedEmissions() {
        List<TacticalEmissionSummaryDto> list = new ArrayList<>(QUERIED_EMISSIONS.get().values());
        QUERIED_EMISSIONS.get().clear();
        return list;
    }

        public static PaginationMetadata drainPaginationMetadata() {
        return drainPaginationMetadata(getCurrentConversationId());
    }

        public static PaginationMetadata drainPaginationMetadata(String convId) {
        ActivePaginationState state = getActiveState(convId);
        log.info("drainPaginationMetadata [convId={}]: pagedToolCalledInTurn={}, hasMore={}, totalCount={}, pageSize={}, lastPage={}",
            convId, state.pagedToolCalledInTurn, state.emissionHasMore, state.emissionTotalCount, state.emissionPageSize, state.lastEmissionPage);
        if (!state.pagedToolCalledInTurn) {
            return null;
        }
        state.pagedToolCalledInTurn = false; // Tek seferlik tur bayrağını tüket

        // KATI KISITLANMA KONTROLÜ:
        // Sayfalama SADECE ve SADECE kayıtların belirli bir kısmı gelip kısıtlandığında
        // (totalCount > pageSize) ve arkada gerçekten devam eden kayıt olduğunda (hasMore == true) anlamlıdır!
        if (!state.emissionHasMore || state.emissionTotalCount <= state.emissionPageSize || state.lastEmissionPage < 0) {
            return null;
        }

        return new PaginationMetadata(
            true,
            state.lastEmissionPage,
            state.emissionTotalPages,
            state.emissionTotalCount,
            state.emissionPageSize,
            "EMISSIONS",
            "Sonraki sayfadaki hedefleri listelemek için 'sonraki sayfayı getir' veya 'devamını göster' diyebilirsiniz."
        );
    }

    public static PaginationMetadata getPaginationMetadata() {
        return drainPaginationMetadata();
    }

    public List<TacticalEmissionSummaryDto> getEmissionsForCop(int limit) {
        Page<TacticalEmissionEntity> pageResult = emissionRepo.findAll(
            PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "sonTespitZamani"))
        );
        return pageResult.getContent().stream().map(this::toSummaryDto).collect(Collectors.toList());
    }

    private final TacticalEmissionJpaRepository emissionRepo;
    private final TacticalComintJpaRepository comintRepo;
    private final ObjectMapper objectMapper;

    public RadarEmissionTools(
        TacticalEmissionJpaRepository emissionRepo,
        TacticalComintJpaRepository comintRepo,
        ObjectMapper objectMapper
    ) {
        this.emissionRepo = emissionRepo;
        this.comintRepo = comintRepo;
        this.objectMapper = objectMapper;
    }

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
            trimmed.equalsIgnoreCase("HEPSİ") ||
            trimmed.equalsIgnoreCase("TUMU") ||
            trimmed.equalsIgnoreCase("HEPSI")) {
            return null;
        }
        return trimmed;
    }

        @Tool(description = "Sahadaki tum taktik radar emisyonlarinin makro ozet istatistiklerini doner. getTacticalMacroStats ile aynidir.")
    public Map<String, Object> queryTacticalMacroStats() {
        return getTacticalMacroStats();
    }

    @Tool(description = "Sahadaki tum taktik radar emisyonlarinin makro ozet istatistiklerini doner. Teshis (Dost/Dusman), Veri Kaynagi, Platform Ortami, Aktif Elektronik Taarruz (ET) ve Taciz sayilarini icerir.")
    public Map<String, Object> getTacticalMacroStats() {
        CALL_LOGS.get().add("Tool Çağrısı: getTacticalMacroStats() çalıştırıldı.");

        long totalCount = emissionRepo.count();
        long tacizCount = emissionRepo.countByTacizTrue();
        long etUygulaniyorCount = emissionRepo.countByEtUygulamaDurumu(EtUygulamaDurumu.UYGULANIYOR);
        long etSusturduCount = emissionRepo.countByEtUygulamaDurumu(EtUygulamaDurumu.SUSTURDU);

        Map<String, Long> teshisMap = new LinkedHashMap<>();
        for (Object[] row : emissionRepo.getTeshisDistribution()) {
            teshisMap.put(String.valueOf(row[0]), (Long) row[1]);
        }

        Map<String, Long> veriKaynagiMap = new LinkedHashMap<>();
        for (Object[] row : emissionRepo.getVeriKaynagiDistribution()) {
            veriKaynagiMap.put(String.valueOf(row[0]), (Long) row[1]);
        }

        Map<String, Long> platformMap = new LinkedHashMap<>();
        for (Object[] row : emissionRepo.getPlatformOrtamiDistribution()) {
            platformMap.put(String.valueOf(row[0]), (Long) row[1]);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toplamEmisyonSayisi", totalCount);
        result.put("tacizYapanHedefSayisi", tacizCount);
        result.put("aktifEtUygulananSayisi", etUygulaniyorCount);
        result.put("susturulanHedefSayisi", etSusturduCount);
        result.put("teshisDagilimi", teshisMap);
        result.put("veriKaynagiDagilimi", veriKaynagiMap);
        result.put("platformOrtamiDagilimi", platformMap);

        return result;
    }

    @Tool(description = "En kritik öncelikli tehditleri listeler: Teşhisi DÜŞMAN veya MUHTEMEL_DÜŞMAN olan, taciz yapan veya HSS içeren en güncel ilk 15 hedefi döner.")
    public List<TacticalEmissionSummaryDto> queryPriorityThreats(Integer limit) {
        int max = (limit == null || limit <= 0) ? 15 : Math.min(limit, 30);
        CALL_LOGS.get().add("Tool Çağrısı: queryPriorityThreats(limit=" + max + ") çalıştırıldı.");

        Specification<TacticalEmissionEntity> spec = (root, query, cb) -> {
            Predicate isEnemy = root.get("teshisKimlik").in(TeshisKimlik.DUSMAN, TeshisKimlik.MUHTEMEL_DUSMAN);
            Predicate isTaciz = cb.isTrue(root.get("taciz"));
            Predicate hasHss = cb.isNotNull(root.get("hss"));
            return cb.or(isTaciz, cb.and(isEnemy, hasHss));
        };

        Page<TacticalEmissionEntity> page = emissionRepo.findAll(
            spec,
            PageRequest.of(0, max, Sort.by(Sort.Direction.DESC, "sonTespitZamani"))
        );

        List<TacticalEmissionSummaryDto> dtoList = page.getContent().stream().map(this::toSummaryDto).collect(Collectors.toList());
        dtoList.forEach(d -> QUERIED_EMISSIONS.get().put(d.id(), d));
        return dtoList;
    }

    @Tool(description = "Taktik radar emisyonlarını çoklu kriterlerle ve ARALIKLI filtrelerle (Frekans min-max, PRI min-max, PW min-max, ATP min-max, Teşhis, Veri Kaynağı, Radar Adı, HSS, Taciz, ET Durumu) sayfalı olarak sorgular.")
    public PagedResult<TacticalEmissionSummaryDto> queryEmissionsPaged(
        String teshisKimlik,
        String veriKaynagi,
        String radarAdi,
        String hss,
        Boolean taciz,
        String etUygulamaDurumu,
        String platformOrtami,
        Double minFrekansMhz,
        Double maxFrekansMhz,
        String frekansTipi,
        Double minPriMicroSec,
        Double maxPriMicroSec,
        Double minPwMicroSec,
        Double maxPwMicroSec,
        Double minAtpMicroSec,
        Double maxAtpMicroSec,
        Integer sayfaNo,
        Integer sayfaBoyutu
    ) {
        String cleanTeshis = cleanParam(teshisKimlik);
        String cleanKaynak = cleanParam(veriKaynagi);
        String cleanRadar = cleanParam(radarAdi);
        String cleanHss = cleanParam(hss);
        String cleanEt = cleanParam(etUygulamaDurumu);
        String cleanPlatform = cleanParam(platformOrtami);
        String cleanFreqType = cleanParam(frekansTipi);

        // LLM'ler ve kullanıcılar ilk sayfayı genellikle 1 olarak gönderir.
        int pageIdx = (sayfaNo == null || sayfaNo <= 0) ? 0 : (sayfaNo >= 1 ? sayfaNo - 1 : 0);
        int size = (sayfaBoyutu == null || sayfaBoyutu <= 0) ? 15 : Math.min(sayfaBoyutu, 50);

        ActivePaginationState state = getActiveState();
        state.lastTeshis = cleanTeshis;
        state.lastVeriKaynagi = cleanKaynak;
        state.lastRadarAdi = cleanRadar;
        state.lastHss = cleanHss;
        state.lastTaciz = taciz;
        state.lastEtUygulamaDurumu = cleanEt;
        state.lastPlatformOrtami = cleanPlatform;
        state.lastMinFrekansMhz = minFrekansMhz;
        state.lastMaxFrekansMhz = maxFrekansMhz;
        state.lastFrekansTipi = cleanFreqType;
        state.lastMinPriMicroSec = minPriMicroSec;
        state.lastMaxPriMicroSec = maxPriMicroSec;
        state.lastMinPwMicroSec = minPwMicroSec;
        state.lastMaxPwMicroSec = maxPwMicroSec;
        state.lastMinAtpMicroSec = minAtpMicroSec;
        state.lastMaxAtpMicroSec = maxAtpMicroSec;
        state.lastEmissionPage = pageIdx;
        state.emissionPageSize = size;

        state.pagedToolCalledInTurn = true;
        log.info("queryEmissionsPaged FULL: teshis={}, kaynak={}, radar={}, hss={}, taciz={}, et={}, platform={}, freq=[{}-{}], pri=[{}-{}], pw=[{}-{}], atp=[{}-{}], page={}, size={}",
            cleanTeshis, cleanKaynak, cleanRadar, cleanHss, taciz, cleanEt, cleanPlatform,
            minFrekansMhz, maxFrekansMhz, minPriMicroSec, maxPriMicroSec,
            minPwMicroSec, maxPwMicroSec, minAtpMicroSec, maxAtpMicroSec, pageIdx, size);
        CALL_LOGS.get().add(String.format("Tool: queryEmissionsPaged(teshis=%s, kaynak=%s, freq=[%s-%s], pri=[%s-%s], taciz=%s, page=%d)",
            cleanTeshis, cleanKaynak, minFrekansMhz, maxFrekansMhz, minPriMicroSec, maxPriMicroSec, taciz, pageIdx));

        Specification<TacticalEmissionEntity> spec = createSpecification(
            cleanTeshis, cleanKaynak, cleanRadar, cleanHss, taciz, cleanEt, cleanPlatform,
            minFrekansMhz, maxFrekansMhz, cleanFreqType,
            minPriMicroSec, maxPriMicroSec,
            minPwMicroSec, maxPwMicroSec,
            minAtpMicroSec, maxAtpMicroSec
        );

        Page<TacticalEmissionEntity> pageResult = emissionRepo.findAll(
            spec,
            PageRequest.of(pageIdx, size, Sort.by(Sort.Direction.DESC, "sonTespitZamani"))
        );

        state.emissionTotalCount = pageResult.getTotalElements();
        state.emissionTotalPages = pageResult.getTotalPages();
        state.emissionHasMore = pageResult.hasNext();

        List<TacticalEmissionSummaryDto> dtos = pageResult.getContent().stream().map(this::toSummaryDto).collect(Collectors.toList());
        dtos.forEach(d -> QUERIED_EMISSIONS.get().put(d.id(), d));

        String note = String.format("Sayfa %d / %d (Toplam %d hedef)", pageIdx + 1, pageResult.getTotalPages(), pageResult.getTotalElements());
        return PagedResult.of(dtos, pageIdx, size, pageResult.getTotalElements(), note);
    }

    @Tool(description = "Kullanıcı 'sonraki sayfayı getir', 'sıradakileri göster' veya 'devamını getir' dediğinde önceki arama kriterlerini otomatik hatırlayarak bir sonraki sayfayı (page + 1) çeker.")
    public PagedResult<TacticalEmissionSummaryDto> getNextPageOfEmissions() {
        ActivePaginationState state = getActiveState();
        int targetPage = (state.lastEmissionPage < 0) ? 1 : state.lastEmissionPage + 2; // 1-indexed
        CALL_LOGS.get().add("Tool Çağrısı: getNextPageOfEmissions() -> Sayfa " + targetPage + " isteniyor.");

        return queryEmissionsPaged(
            state.lastTeshis, state.lastVeriKaynagi, state.lastRadarAdi, state.lastHss, state.lastTaciz,
            state.lastEtUygulamaDurumu, state.lastPlatformOrtami,
            state.lastMinFrekansMhz, state.lastMaxFrekansMhz, state.lastFrekansTipi,
            state.lastMinPriMicroSec, state.lastMaxPriMicroSec,
            state.lastMinPwMicroSec, state.lastMaxPwMicroSec,
            state.lastMinAtpMicroSec, state.lastMaxAtpMicroSec,
            targetPage, state.emissionPageSize
        );
    }

    @Tool(description = "Kullanıcı 'önceki sayfaya dön' veya 'bir önceki hedefleri göster' dediğinde önceki arama kriterlerini hatırlayarak önceki sayfayı (page - 1) çeker.")
    public PagedResult<TacticalEmissionSummaryDto> getPrevPageOfEmissions() {
        ActivePaginationState state = getActiveState();
        int targetPage = Math.max(1, (state.lastEmissionPage < 0) ? 1 : state.lastEmissionPage); // 1-indexed
        CALL_LOGS.get().add("Tool Çağrısı: getPrevPageOfEmissions() -> Sayfa " + targetPage + " isteniyor.");

        return queryEmissionsPaged(
            state.lastTeshis, state.lastVeriKaynagi, state.lastRadarAdi, state.lastHss, state.lastTaciz,
            state.lastEtUygulamaDurumu, state.lastPlatformOrtami,
            state.lastMinFrekansMhz, state.lastMaxFrekansMhz, state.lastFrekansTipi,
            state.lastMinPriMicroSec, state.lastMaxPriMicroSec,
            state.lastMinPwMicroSec, state.lastMaxPwMicroSec,
            state.lastMinAtpMicroSec, state.lastMaxAtpMicroSec,
            targetPage, state.emissionPageSize
        );
    }

    @Tool(description = "Elektronik Taarruz (ET) uygulanan, susturulan veya hudut tacizi yapan hedeflerin özel operasyonel durum raporunu getirir.")
    public List<TacticalEmissionSummaryDto> queryElectronicAttackAndHarassment() {
        CALL_LOGS.get().add("Tool: queryElectronicAttackAndHarassment() çalıştırıldı.");

        Specification<TacticalEmissionEntity> spec = (root, query, cb) -> {
            Predicate etActive = cb.equal(root.get("etUygulamaDurumu"), EtUygulamaDurumu.UYGULANIYOR);
            Predicate etSusturdu = cb.equal(root.get("etUygulamaDurumu"), EtUygulamaDurumu.SUSTURDU);
            Predicate isTaciz = cb.isTrue(root.get("taciz"));
            return cb.or(etActive, etSusturdu, isTaciz);
        };

        List<TacticalEmissionEntity> list = emissionRepo.findAll(
            spec,
            Sort.by(Sort.Direction.DESC, "sonTespitZamani")
        );

        List<TacticalEmissionSummaryDto> dtos = list.stream().map(this::toSummaryDto).collect(Collectors.toList());
        dtos.forEach(d -> QUERIED_EMISSIONS.get().put(d.id(), d));
        return dtos;
    }

    @Tool(description = "Belirli bir taktik emisyonun (emissionId) çoklu Frekans Listesini, PRI/PW listelerini, Anten Tarama Deseni (ATP) periyotlarını, operatör ve onay notlarını içeren derin teknik detaylarını döner.")
    public TacticalEmissionDetailDto getEmissionDetails(String emissionId) {
        CALL_LOGS.get().add("Tool: getEmissionDetails(emissionId=" + emissionId + ") çalıştırıldı.");

        TacticalEmissionEntity e = emissionRepo.findById(emissionId)
            .orElseThrow(() -> new IllegalArgumentException("Emisyon bulunamadı: " + emissionId));

        List<FrequencyEntry> freqList = Collections.emptyList();
        List<PriEntry> priList = Collections.emptyList();
        List<PwEntry> pwList = Collections.emptyList();
        List<AtpEntry> atpList = Collections.emptyList();

        try {
            if (e.getFrekansListesiJson() != null) {
                freqList = objectMapper.readValue(e.getFrekansListesiJson(), new TypeReference<>() {});
            }
            if (e.getPriListesiJson() != null) {
                priList = objectMapper.readValue(e.getPriListesiJson(), new TypeReference<>() {});
            }
            if (e.getPwListesiJson() != null) {
                pwList = objectMapper.readValue(e.getPwListesiJson(), new TypeReference<>() {});
            }
            if (e.getAtpListesiJson() != null) {
                atpList = objectMapper.readValue(e.getAtpListesiJson(), new TypeReference<>() {});
            }
        } catch (Exception ex) {
            log.error("JSON parse hatası: {}", ex.getMessage());
        }

        return new TacticalEmissionDetailDto(
            e.getId(),
            e.getVeriKaynagi() != null ? e.getVeriKaynagi().name() : null,
            e.getKuvvetSiraNo(),
            e.getUstBirlikRef(),
            e.getEhUnsuru(),
            e.getYon(),
            e.getEhUnsuruEnlem(),
            e.getEhUnsuruBoylam(),
            e.getEhUnsuruIrtifa(),
            e.getYayinEnlem(),
            e.getYayinBoylam(),
            e.getYayinSemiMajorMeters(),
            e.getYayinSemiMinorMeters(),
            e.getYayinOrientationDegrees(),
            e.getHedefYerBilgisi(),
            e.getHedefMevziBilgisi(),
            e.getTeshisKimlik() != null ? e.getTeshisKimlik().name() : null,
            e.getElintNotasyonu(),
            e.getHedefKaynagi() != null ? e.getHedefKaynagi().name() : null,
            e.getPolarizasyon() != null ? e.getPolarizasyon().name() : null,
            e.getSpotNo(),
            e.getPulseCw() != null ? e.getPulseCw().name() : null,
            e.getRadarGorevi(),
            e.getRadarAdi(),
            e.getPlatformOrtami() != null ? e.getPlatformOrtami().name() : null,
            e.getPlatformTipi() != null ? e.getPlatformTipi().name() : null,
            e.getGenlikDbm(),
            e.getModulasyon() != null ? e.getModulasyon().name() : null,
            e.getEtUygulamaDurumu() != null ? e.getEtUygulamaDurumu().name() : null,
            e.getIlkTespitZamani(),
            e.getSonTespitZamani(),
            e.getSureSn(),
            e.getHss(),
            e.getTaciz(),
            e.getRadarKesitAlani(),
            e.getRadarIzNumarasi(),
            e.getUcakKuyrukNumarasi(),
            e.getMinFrekansMhz(),
            e.getMaxFrekansMhz(),
            e.getMinPriMicroSec(),
            e.getMaxPriMicroSec(),
            e.getMinPwMicroSec(),
            e.getMaxPwMicroSec(),
            e.getMinAtpMicroSec(),
            e.getMaxAtpMicroSec(),
            freqList,
            priList,
            pwList,
            atpList,
            e.getOperatorNotu(),
            e.getOnaylayanKullanici(),
            e.getOnayNotu()
        );
    }

    @Tool(description = "Zaman-Frekans spektral şelale (Scatter/Waterfall) grafiği için ölçüm noktalarını döner. Her nokta: dakika cinsinden zaman, frekans (MHz), teşhis, radar adı ve PRI bilgisini içerir.")
    public List<Map<String, Object>> getTimeFrequencyDistribution(String teshisKimlik, String veriKaynagi, String radarAdi, Integer limit) {
        String cleanTeshis = cleanParam(teshisKimlik);
        String cleanKaynak = cleanParam(veriKaynagi);
        String cleanRadar = cleanParam(radarAdi);
        int max = (limit == null || limit <= 0) ? 250 : Math.min(limit, 500);

        CALL_LOGS.get().add(String.format("Tool: getTimeFrequencyDistribution(teshis=%s, kaynak=%s, radar=%s, limit=%d)", cleanTeshis, cleanKaynak, cleanRadar, max));

        Specification<TacticalEmissionEntity> spec = createSpecification(
            cleanTeshis, cleanKaynak, cleanRadar, null, null, null, null,
            null, null, null, null, null, null, null, null, null
        );

        List<TacticalEmissionEntity> list = emissionRepo.findAll(
            spec,
            PageRequest.of(0, max, Sort.by(Sort.Direction.DESC, "sonTespitZamani"))
        ).getContent();

        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

        List<Map<String, Object>> points = new ArrayList<>();
        for (TacticalEmissionEntity e : list) {
            if (e.getSonTespitZamani() == null || e.getMinFrekansMhz() == null) continue;

            String timeStr = timeFmt.format(e.getSonTespitZamani());
            String[] parts = timeStr.split(":");
            int minutesFromMidnight = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);

            Map<String, Object> p = new LinkedHashMap<>();
            p.put("emissionId", e.getId());
            p.put("timeStr", timeStr);
            p.put("timeMinutes", minutesFromMidnight);
            p.put("frequencyMhz", e.getMinFrekansMhz());
            p.put("maxFrequencyMhz", e.getMaxFrekansMhz());
            p.put("radarAdi", e.getRadarAdi());
            p.put("teshisKimlik", e.getTeshisKimlik() != null ? e.getTeshisKimlik().name() : "BILINMEYEN");
            p.put("taciz", e.getTaciz());
            p.put("etUygulamaDurumu", e.getEtUygulamaDurumu() != null ? e.getEtUygulamaDurumu().name() : "YOK");
            p.put("priMicroSec", e.getMinPriMicroSec());
            p.put("pwMicroSec", e.getMinPwMicroSec());
            points.add(p);
        }

        return points;
    }

    private TeshisKimlik normalizeTeshis(String val) {
        if (val == null) return null;
        String v = val.trim().toUpperCase(Locale.ROOT)
            .replace('ı', 'I').replace('İ', 'I')
            .replace('ö', 'O').replace('Ö', 'O')
            .replace('ü', 'U').replace('Ü', 'U')
            .replace('ş', 'S').replace('Ş', 'S')
            .replace('ç', 'C').replace('Ç', 'C')
            .replace('ğ', 'G').replace('Ğ', 'G');
        if (v.contains("MUHTEMEL") && (v.contains("DUS") || v.contains("HOSTILE"))) return TeshisKimlik.MUHTEMEL_DUSMAN;
        if (v.contains("MUHTEMEL") && (v.contains("DOST") || v.contains("FRIEND"))) return TeshisKimlik.MUHTEMEL_DOST;
        if (v.contains("DUS") || v.contains("TEHDIT") || v.contains("HOSTILE") || v.contains("ENEMY") || v.contains("D?")) return TeshisKimlik.DUSMAN;
        if (v.contains("DOST") || v.contains("FRIEND")) return TeshisKimlik.DOST;
        if (v.contains("BILIN") || v.contains("UNKNOWN")) return TeshisKimlik.BILINMEYEN;
        if (v.contains("TARAF") || v.contains("NEUTRAL")) return TeshisKimlik.TARAFSIZ;
        try { return TeshisKimlik.valueOf(v); } catch (Exception ignored) { return null; }
    }

    private VeriKaynagi normalizeVeriKaynagi(String val) {
        if (val == null) return null;
        String v = val.toUpperCase().replace("İ", "I");
        if (v.contains("DENIZ")) return VeriKaynagi.DENIZ;
        if (v.contains("KARA")) return VeriKaynagi.KARA;
        if (v.contains("HAVA")) return VeriKaynagi.HAVA;
        if (v.contains("GEN")) return VeriKaynagi.GEN_KUR;
        try { return VeriKaynagi.valueOf(v); } catch (Exception ignored) { return null; }
    }

    private PlatformOrtami normalizePlatformOrtami(String val) {
        if (val == null) return null;
        String v = val.toUpperCase().replace("İ", "I").replace("Ü", "U");
        if (v.contains("HAVA")) return PlatformOrtami.HAVA;
        if (v.contains("KARA")) return PlatformOrtami.KARA;
        if (v.contains("DENIZ") || v.contains("SU_USTU") || v.contains("SU USTU")) return PlatformOrtami.SU_USTU;
        if (v.contains("SU_ALTI") || v.contains("SU ALTI")) return PlatformOrtami.SU_ALTI;
        try { return PlatformOrtami.valueOf(v); } catch (Exception ignored) { return null; }
    }

    private EtUygulamaDurumu normalizeEtDurumu(String val) {
        if (val == null) return null;
        String v = val.toUpperCase().replace("İ", "I").replace("Ş", "S");
        if (v.contains("SUSTUR")) return EtUygulamaDurumu.SUSTURDU;
        if (v.contains("UYGULAN") || v.contains("AKTIF")) return EtUygulamaDurumu.UYGULANIYOR;
        if (v.contains("YOK") || v.contains("UYGULANMIYOR")) return EtUygulamaDurumu.UYGULANMIYOR;
        try { return EtUygulamaDurumu.valueOf(v); } catch (Exception ignored) { return null; }
    }

    private Specification<TacticalEmissionEntity> createSpecification(
        String teshisKimlik, String veriKaynagi, String radarAdi, String hss, Boolean taciz,
        String etUygulamaDurumu, String platformOrtami,
        Double minFreq, Double maxFreq, String freqType,
        Double minPri, Double maxPri,
        Double minPw, Double maxPw,
        Double minAtp, Double maxAtp
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            TeshisKimlik tk = normalizeTeshis(teshisKimlik);
            if (tk != null) {
                predicates.add(cb.equal(root.get("teshisKimlik"), tk));
            }

            VeriKaynagi vk = normalizeVeriKaynagi(veriKaynagi);
            if (vk != null) {
                predicates.add(cb.equal(root.get("veriKaynagi"), vk));
            }

            PlatformOrtami po = normalizePlatformOrtami(platformOrtami);
            if (po != null) {
                predicates.add(cb.equal(root.get("platformOrtami"), po));
            }

            EtUygulamaDurumu et = normalizeEtDurumu(etUygulamaDurumu);
            if (et != null) {
                predicates.add(cb.equal(root.get("etUygulamaDurumu"), et));
            }

            if (radarAdi != null) {
                predicates.add(cb.like(cb.lower(root.get("radarAdi")), "%" + radarAdi.toLowerCase() + "%"));
            }

            if (hss != null) {
                predicates.add(cb.like(cb.lower(root.get("hss")), "%" + hss.toLowerCase() + "%"));
            }

            if (Boolean.TRUE.equals(taciz)) {
                predicates.add(cb.isTrue(root.get("taciz")));
            }

            if (etUygulamaDurumu != null) {
                try {
                    predicates.add(cb.equal(root.get("etUygulamaDurumu"), EtUygulamaDurumu.valueOf(etUygulamaDurumu.toUpperCase())));
                } catch (IllegalArgumentException ignored) {}
            }

            if (platformOrtami != null) {
                try {
                    predicates.add(cb.equal(root.get("platformOrtami"), PlatformOrtami.valueOf(platformOrtami.toUpperCase())));
                } catch (IllegalArgumentException ignored) {}
            }

                        // Dummy / Halüsinasyon sınır temizliği
            Double effectiveMinFreq = (minFreq != null && minFreq > 1.0) ? minFreq : null;
            Double effectiveMaxFreq = (maxFreq != null && maxFreq < 50000.0) ? maxFreq : null;
            Double effectiveMinPri = (minPri != null && minPri > 1.0) ? minPri : null;
            Double effectiveMaxPri = (maxPri != null && maxPri < 50000.0) ? maxPri : null;
            Double effectiveMinPw = (minPw != null && minPw > 0.01) ? minPw : null;
            Double effectiveMaxPw = (maxPw != null && maxPw < 5000.0) ? maxPw : null;
            Double effectiveMinAtp = (minAtp != null && minAtp > 10000.0) ? minAtp : null;
            Double effectiveMaxAtp = (maxAtp != null && maxAtp > 1000000.0 && maxAtp < 50000000.0) ? maxAtp : null;

            // 📡 Frekans Aralığı Kesişim Mantığı: target.min <= query.max AND target.max >= query.min
            if (effectiveMinFreq != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxFrekansMhz"), effectiveMinFreq));
            }
            if (effectiveMaxFreq != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("minFrekansMhz"), effectiveMaxFreq));
            }
            if (freqType != null) {
                predicates.add(cb.like(cb.lower(root.get("frekansListesiJson")), "%" + freqType.toLowerCase() + "%"));
            }

            // ⏱️ PRI Aralığı Kesişim Mantığı
            if (effectiveMinPri != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxPriMicroSec"), effectiveMinPri));
            }
            if (effectiveMaxPri != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("minPriMicroSec"), effectiveMaxPri));
            }

            // 📏 PW Aralığı Kesişim Mantığı
            if (effectiveMinPw != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxPwMicroSec"), effectiveMinPw));
            }
            if (effectiveMaxPw != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("minPwMicroSec"), effectiveMaxPw));
            }

            // 🔄 ATP Aralığı Kesişim Mantığı
            if (effectiveMinAtp != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxAtpMicroSec"), effectiveMinAtp));
            }
            if (effectiveMaxAtp != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("minAtpMicroSec"), effectiveMaxAtp));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public TacticalEmissionSummaryDto toSummaryDto(TacticalEmissionEntity e) {
        return toStaticDto(e);
    }

    public static TacticalEmissionSummaryDto toStaticDto(TacticalEmissionEntity e) {
        return new TacticalEmissionSummaryDto(
            e.getId(),
            e.getVeriKaynagi() != null ? e.getVeriKaynagi().name() : null,
            e.getKuvvetSiraNo(),
            e.getEhUnsuru(),
            e.getYon(),
            e.getEhUnsuruEnlem(),
            e.getEhUnsuruBoylam(),
            e.getTeshisKimlik() != null ? e.getTeshisKimlik().name() : null,
            e.getRadarAdi(),
            e.getRadarGorevi(),
            e.getHss(),
            e.getTaciz(),
            e.getEtUygulamaDurumu() != null ? e.getEtUygulamaDurumu().name() : null,
            e.getPlatformOrtami() != null ? e.getPlatformOrtami().name() : null,
            e.getPlatformTipi() != null ? e.getPlatformTipi().name() : null,
            e.getHedefYerBilgisi(),
            e.getHedefMevziBilgisi(),
            e.getMinFrekansMhz(),
            e.getMaxFrekansMhz(),
            e.getMinPriMicroSec(),
            e.getMaxPriMicroSec(),
            e.getMinPwMicroSec(),
            e.getMaxPwMicroSec(),
            e.getMinAtpMicroSec(),
            e.getMaxAtpMicroSec(),
            e.getSonTespitZamani(),
            e.getYayinEnlem(),
            e.getYayinBoylam(),
            e.getYayinSemiMajorMeters(),
            e.getYayinSemiMinorMeters(),
            e.getYayinOrientationDegrees(),
            e.getSpotNo(),
            e.getElintNotasyonu(),
            e.getPolarizasyon() != null ? e.getPolarizasyon().name() : null,
            e.getPulseCw() != null ? e.getPulseCw().name() : null,
            e.getModulasyon() != null ? e.getModulasyon().name() : null,
            e.getGenlikDbm(),
            e.getSureSn(),
            e.getUcakKuyrukNumarasi(),
            e.getOperatorNotu(),
            e.getOnaylayanKullanici(),
            e.getOnayNotu()
        );
    }

    @Tool(description = "Radar ve Muhabere (COMINT) emisyonlari arasinda cografi/mekansal korelasyon ve cok kaynakli fuzyon analizi yapar. Hata elipslerinin (CEP) geometrik kesisimini (overlap), ceperden cepere mesafesini ve Mahalanobis fuzyonunu hesaplayarak ayni veya yakin mevzilenmis Entegre Hava Savunma / Komuta Dugumlerini tespit eder.")
    public Map<String, Object> getCrossDomainCorrelations(Double maxDistanceKm, Integer limit) {
        double thresholdKm = (maxDistanceKm != null && maxDistanceKm > 0) ? maxDistanceKm : 5.0;
        int maxResults = (limit != null && limit > 0) ? limit : 20;

        log.info("Tool Cagrisi: getCrossDomainCorrelations(maxDistanceKm={}, limit={}) calistirildi.", thresholdKm, maxResults);
        CALL_LOGS.get().add("Tool Cagrisi: getCrossDomainCorrelations() ile Hata Elipsi (CEP) geometrik örtüşme ve füzyon analizi yapıldı.");

        List<TacticalEmissionEntity> radars = emissionRepo.findAll();
        List<TacticalComintEntity> comints = comintRepo.findAll();

        List<Map<String, Object>> correlations = new ArrayList<>();
        int totalOverlappingCount = 0;
        int totalStatisticalCoLocatedCount = 0;

        for (TacticalEmissionEntity r : radars) {
            if (r.getYayinEnlem() == null || r.getYayinBoylam() == null) continue;
            GeoPoint rPoint = new GeoPoint(r.getYayinEnlem(), r.getYayinBoylam(), 0.0);
            double rMajor = r.getYayinSemiMajorMeters() != null ? r.getYayinSemiMajorMeters() : 500.0;
            double rMinor = r.getYayinSemiMinorMeters() != null ? r.getYayinSemiMinorMeters() : 250.0;
            double rOrient = r.getYayinOrientationDegrees() != null ? r.getYayinOrientationDegrees() : 0.0;

            for (TacticalComintEntity c : comints) {
                if (c.getYayinEnlem() == null || c.getYayinBoylam() == null) continue;
                GeoPoint cPoint = new GeoPoint(c.getYayinEnlem(), c.getYayinBoylam(), 0.0);
                double cMajor = c.getYayinSemiMajorMeters() != null ? c.getYayinSemiMajorMeters() : 3000.0;
                double cMinor = c.getYayinSemiMinorMeters() != null ? c.getYayinSemiMinorMeters() : 1000.0;
                double cOrient = c.getYayinOrientationDegrees() != null ? c.getYayinOrientationDegrees() : 0.0;

                GeoUtils.EllipseSpatialRelation relation = GeoUtils.calculateEllipseSpatialRelation(
                    rPoint, rMajor, rMinor, rOrient,
                    cPoint, cMajor, cMinor, cOrient
                );

                double boundaryKm = relation.boundaryDistanceMeters() / 1000.0;
                double centerKm = relation.centerDistanceMeters() / 1000.0;

                // Elipsler kesisiyorsa VEYA ceperden cepere mesafe esigin altindaysa eslesme kabul edilir!
                if (relation.isOverlapping() || boundaryKm <= thresholdKm) {
                    if (relation.isOverlapping()) {
                        totalOverlappingCount++;
                    }
                    if (relation.isStatisticallyCoLocated95()) {
                        totalStatisticalCoLocatedCount++;
                    }

                    Map<String, Object> pair = new LinkedHashMap<>();
                    pair.put("ceperMesafeKm", Math.round(boundaryKm * 100.0) / 100.0);
                    pair.put("merkezMesafeKm", Math.round(centerKm * 100.0) / 100.0);
                    pair.put("elipslerOrtusuyorMu", relation.isOverlapping());
                    pair.put("ortusmeMetre", Math.round(relation.overlapMeters()));
                    pair.put("ortusmeYuzdesi", Math.round(relation.overlapPercentage() * 10.0) / 10.0);
                    pair.put("istatistikselAyniMevzi95", relation.isStatisticallyCoLocated95());
                    pair.put("mahalanobisSkoru", Math.round(relation.mahalanobisDistanceSq() * 100.0) / 100.0);

                    String fusionType;
                    if (relation.isOverlapping() && relation.isStatisticallyCoLocated95()) {
                        fusionType = "TAM_ORTUSME_VE_STATISTIKSEL_AYNI_MEVZI";
                    } else if (relation.isOverlapping()) {
                        fusionType = "ELIPSLER_KESISIYOR_ORTUSME_VAR";
                    } else {
                        fusionType = "YAKIN_KOMSU_MEVZI";
                    }
                    pair.put("fuzyonTipi", fusionType);

                    pair.put("radarId", r.getId());
                    pair.put("radarAdi", r.getRadarAdi() != null ? r.getRadarAdi() : "Bilinmeyen Radar");
                    pair.put("radarTeshis", r.getTeshisKimlik() != null ? r.getTeshisKimlik().name() : "BILINMEYEN");
                    pair.put("hss", r.getHss() != null ? r.getHss() : "-");
                    pair.put("radarMevzi", r.getHedefYerBilgisi() != null ? r.getHedefYerBilgisi() : "-");
                    pair.put("radarCepMajorMetre", Math.round(rMajor));
                    pair.put("radarCepMinorMetre", Math.round(rMinor));

                    pair.put("comintId", c.getId());
                    pair.put("cagriAdi", c.getCagriAdi() != null ? c.getCagriAdi() : "-");
                    pair.put("karsiCagriAdi", c.getKarsiCagriAdi() != null ? c.getKarsiCagriAdi() : "-");
                    pair.put("lisan", c.getLisan() != null ? c.getLisan() : "-");
                    pair.put("protokol", c.getProtokol() != null ? c.getProtokol().name() : "-");
                    pair.put("haberlesmeSekli", c.getHaberlesmeSekli() != null ? c.getHaberlesmeSekli().name() : "-");
                    pair.put("comintCepMajorMetre", Math.round(cMajor));
                    pair.put("comintCepMinorMetre", Math.round(cMinor));

                    String tacticalEstimate = "Ortak Taktik Mevzi";
                    if (r.getHss() != null && !r.getHss().isBlank()) {
                        tacticalEstimate = "Entegre HSS Bataryasi & Ates Idare Telsiz Postasi (" + r.getHss() + ")";
                    } else if (r.getRadarGorevi() != null && r.getRadarGorevi().contains("ERKEN_UYARI")) {
                        tacticalEstimate = "Erken Uyari Radar Mevzii & Bolge Muhabere Postasi";
                    }
                    pair.put("taktikRolTahmini", tacticalEstimate);

                    correlations.add(pair);
                    QUERIED_EMISSIONS.get().put(r.getId(), toSummaryDto(r));
                    ComintEmissionTools.putQueriedComint(ComintEmissionTools.toStaticDto(c));
                }
            }
        }

        correlations.sort((a, b) -> {
            boolean aOver = (boolean) a.get("elipslerOrtusuyorMu");
            boolean bOver = (boolean) b.get("elipslerOrtusuyorMu");
            if (aOver && !bOver) return -1;
            if (!aOver && bOver) return 1;
            if (aOver && bOver) {
                return Double.compare((double) b.get("ortusmeMetre"), (double) a.get("ortusmeMetre"));
            }
            return Double.compare((double) a.get("ceperMesafeKm"), (double) b.get("ceperMesafeKm"));
        });

        List<Map<String, Object>> limitedList = correlations.stream().limit(maxResults).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("kriterMesafeKm", thresholdKm);
        response.put("toplamEslesenCiftSayisi", correlations.size());
        response.put("elipsleriBirebirOrtusenCiftSayisi", totalOverlappingCount);
        response.put("istatistiksel95AyniMevziSayisi", totalStatisticalCoLocatedCount);
        response.put("listelenenCiftler", limitedList);
        response.put("ozetDegerlendirme", "Hata elipsi (CEP) geometrisi hesaba katildiginda, sahada " + totalOverlappingCount +
            " ciftin elipslerinin BIREBIR KESISIP ORTUSTUGU ve " + totalStatisticalCoLocatedCount +
            " ciftin %95 guvenle ayni operasyonel mevzide konuslandigi tespit edilmistir. Sadece merkez mesafesine bakilmayip elips sinirlari ve olasilik dagilimi entegre edilmistir.");

        return response;
    }

    @Tool(description = "Sahanin Elektronik Taarruz (ET / Jamming) etkinlik durumunu ve radar tacizlerini detayli analiz eder. Karistirmaya karsi susturulan hedefleri, devam eden karistirmalari, basari oranini ve hudut tacizi yapan hava unsurlarini doner.")
    public Map<String, Object> getHarassmentAndElectronicAttackAssessment() {
        log.info("Tool Cagrisi: getHarassmentAndElectronicAttackAssessment() calistirildi.");
        CALL_LOGS.get().add("Tool Cagrisi: getHarassmentAndElectronicAttackAssessment() ile ET ve Taciz Durumu cikarildi.");

        long susturulan = emissionRepo.countByEtUygulamaDurumu(EtUygulamaDurumu.SUSTURDU);
        long uygulaniyor = emissionRepo.countByEtUygulamaDurumu(EtUygulamaDurumu.UYGULANIYOR);
        long uygulanmiyor = emissionRepo.countByEtUygulamaDurumu(EtUygulamaDurumu.UYGULANMIYOR);
        long taciz = emissionRepo.countByTacizTrue();
        long total = emissionRepo.count();

        long etTotalTarget = susturulan + uygulaniyor;
        double etSuccessRate = etTotalTarget > 0 ? Math.round((susturulan * 100.0) / etTotalTarget) : 0.0;

        List<TacticalEmissionEntity> all = emissionRepo.findAll();
        List<Map<String, Object>> tacizList = new ArrayList<>();
        List<Map<String, Object>> jammedList = new ArrayList<>();

        for (TacticalEmissionEntity e : all) {
            if (Boolean.TRUE.equals(e.getTaciz())) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", e.getId());
                item.put("radarAdi", e.getRadarAdi());
                item.put("teshis", e.getTeshisKimlik() != null ? e.getTeshisKimlik().name() : "BILINMEYEN");
                item.put("platformOrtami", e.getPlatformOrtami() != null ? e.getPlatformOrtami().name() : "-");
                item.put("kuyrukNo", e.getUcakKuyrukNumarasi() != null ? e.getUcakKuyrukNumarasi() : "-");
                item.put("rcs", e.getRadarKesitAlani() != null ? e.getRadarKesitAlani() : "-");
                item.put("hss", e.getHss() != null ? e.getHss() : "-");
                item.put("mevzi", e.getHedefYerBilgisi() != null ? e.getHedefYerBilgisi() : "-");
                tacizList.add(item);
            }
            if (e.getEtUygulamaDurumu() == EtUygulamaDurumu.SUSTURDU || e.getEtUygulamaDurumu() == EtUygulamaDurumu.UYGULANIYOR) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", e.getId());
                item.put("radarAdi", e.getRadarAdi());
                item.put("etDurumu", e.getEtUygulamaDurumu().name());
                item.put("genlikDbm", e.getGenlikDbm());
                item.put("spotNo", e.getSpotNo());
                jammedList.add(item);
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("toplamRadarSayisi", total);
        res.put("aktifEtUygulananSayisi", uygulaniyor);
        res.put("susturulanHedefSayisi", susturulan);
        res.put("etUygulanmayanSayisi", uygulanmiyor);
        res.put("etBasariOraniYuzde", etSuccessRate);
        res.put("tacizYapanHedefSayisi", taciz);
        res.put("ornekTacizHedefleri", tacizList.stream().limit(8).collect(Collectors.toList()));
        res.put("ornekEtHedefleri", jammedList.stream().limit(8).collect(Collectors.toList()));
        all.stream()
            .filter(e -> Boolean.TRUE.equals(e.getTaciz()) || e.getEtUygulamaDurumu() == EtUygulamaDurumu.SUSTURDU || e.getEtUygulamaDurumu() == EtUygulamaDurumu.UYGULANIYOR)
            .limit(25)
            .forEach(e -> QUERIED_EMISSIONS.get().put(e.getId(), toSummaryDto(e)));

        res.put("harekatTavsiyesi", etSuccessRate < 50 ? "ET Basari Orani dusuk (% " + etSuccessRate + "). Karistirici frekans guc seviyesinin ve huzme yonlendirmenin (beamforming) artirilmasi tavsiye edilir." : "ET soft-kill etkinligi yuksek. Susturulan hedeflerin emisyon tekrari yapip yapmadigi RESM ile gozetlenmelidir.");

        return res;
    }

    @Tool(description = "Sahadaki radarlarin frekans atlama (Agile Hop), darbe tekrarlama (PRI Stagger/Jitter) ve darbe sikistirma modullerini analiz ederek teknolojik ceviklik seviyelerini (Agility Tier) belirler.")
    public Map<String, Object> getRadarAgilityAndSignatureAnalysis() {
        log.info("Tool Cagrisi: getRadarAgilityAndSignatureAnalysis() calistirildi.");
        CALL_LOGS.get().add("Tool Cagrisi: getRadarAgilityAndSignatureAnalysis() ile Radar Ceviklik Analizi yapildi.");

        List<TacticalEmissionEntity> all = emissionRepo.findAll();
        int tier1Cevik = 0;
        int tier2Orta = 0;
        int tier3Standart = 0;

        List<Map<String, Object>> agileRadars = new ArrayList<>();

        for (TacticalEmissionEntity e : all) {
            double bw = 0.0;
            if (e.getMinFrekansMhz() != null && e.getMaxFrekansMhz() != null) {
                bw = e.getMaxFrekansMhz() - e.getMinFrekansMhz();
            }
            double priSpread = 0.0;
            if (e.getMinPriMicroSec() != null && e.getMaxPriMicroSec() != null) {
                priSpread = e.getMaxPriMicroSec() - e.getMinPriMicroSec();
            }

            boolean freqAgile = bw >= 80.0 || (e.getFrekansListesiJson() != null && e.getFrekansListesiJson().contains(","));
            boolean priAgile = priSpread >= 20.0 || (e.getPriListesiJson() != null && e.getPriListesiJson().contains(","));

            String tier;
            if (freqAgile && priAgile) {
                tier = "TIER_1_YUKSEK_CEVIK";
                tier1Cevik++;
            } else if (freqAgile || priAgile) {
                tier = "TIER_2_ORTA_CEVIK";
                tier2Orta++;
            } else {
                tier = "TIER_3_KONVANSIYONEL";
                tier3Standart++;
            }

            if (freqAgile || priAgile) {
                Map<String, Object> rad = new LinkedHashMap<>();
                rad.put("id", e.getId());
                rad.put("radarAdi", e.getRadarAdi());
                rad.put("teshis", e.getTeshisKimlik() != null ? e.getTeshisKimlik().name() : "BILINMEYEN");
                rad.put("frekansBandwidthMhz", Math.round(bw * 10.0) / 10.0);
                rad.put("priSpreadMicroSec", Math.round(priSpread * 10.0) / 10.0);
                rad.put("modulasyon", e.getModulasyon() != null ? e.getModulasyon().name() : "SABIT");
                rad.put("pulseCw", e.getPulseCw() != null ? e.getPulseCw().name() : "PULSE");
                rad.put("ceviklikSinifi", tier);
                agileRadars.add(rad);
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("toplamIncelenenRadar", all.size());
        res.put("tier1YuksekCevikSayisi", tier1Cevik);
        res.put("tier2OrtaCevikSayisi", tier2Orta);
        res.put("tier3KonvansiyonelSayisi", tier3Standart);
        res.put("cevikRadarOrnekleri", agileRadars.stream().limit(10).collect(Collectors.toList()));
        res.put("taktikDegerlendirme", "Sahada " + (tier1Cevik + tier2Orta) + " adet frekans atlamali / PRI degiskenli cevik radar tespit edilmistir. Bu sistemlere karsi sayisal radyo frekans hafizali (DRFM) karistiricilar tavsiye edilir.");

        return res;
    }

    @Tool(description = "Elektronik Muharebe Duzeni (EOB - Electronic Order of Battle) ozetini, radar gorev dagilimini, platform ortamlari ve hava savunma bataryalarinin (HSS) yapisini hiyerarsik olarak cikarir.")
    public Map<String, Object> getElectronicOrderOfBattleSummary() {
        log.info("Tool Cagrisi: getElectronicOrderOfBattleSummary() calistirildi.");
        CALL_LOGS.get().add("Tool Cagrisi: getElectronicOrderOfBattleSummary() ile EOB Hiyerarsisi cikarildi.");

        List<TacticalEmissionEntity> all = emissionRepo.findAll();
        Map<String, Long> gorevCount = new LinkedHashMap<>();
        Map<String, Long> hssCount = new LinkedHashMap<>();
        Map<String, Long> platformCount = new LinkedHashMap<>();
        Map<String, Long> teshisCount = new LinkedHashMap<>();

        for (TacticalEmissionEntity e : all) {
            String g = e.getRadarGorevi() != null ? e.getRadarGorevi() : "TANIMSIZ";
            gorevCount.put(g, gorevCount.getOrDefault(g, 0L) + 1);

            if (e.getHss() != null && !e.getHss().isBlank()) {
                hssCount.put(e.getHss(), hssCount.getOrDefault(e.getHss(), 0L) + 1);
            }

            String p = e.getPlatformOrtami() != null ? e.getPlatformOrtami().name() : "KARA";
            platformCount.put(p, platformCount.getOrDefault(p, 0L) + 1);

            String t = e.getTeshisKimlik() != null ? e.getTeshisKimlik().name() : "BILINMEYEN";
            teshisCount.put(t, teshisCount.getOrDefault(t, 0L) + 1);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("toplamRadarEmisyonu", all.size());
        res.put("teshisDagilimi", teshisCount);
        res.put("platformOrtamiDagilimi", platformCount);
        res.put("radarGorevDagilimi", gorevCount);
        res.put("hssHavaSavunmaBataryalari", hssCount);
        res.put("eobTaktikSonuc", "Hava Savunma aginda " + hssCount.size() + " farkli HSS bataryasi tespit edilmistir. Katmanli erken uyari ve atis kontrol kalkanini kirmak icin SEAD/DEAD harekat planlamasi gerekir.");

        return res;
    }

    @Tool(description = "Sahadaki radarlarin operasyonel yayin pencerelerini (transmission windows), 24 saatlik yayin penceresi yogunlugunu, darbe gorev dongusunu (Duty Cycle), tepe aktivite zamanlarini (peak windows) ve kritik radar acilis-kapanis surelerini detayli analiz eder.")
    public Map<String, Object> getOperationalTransmissionWindows() {
        log.info("Tool Cagrisi: getOperationalTransmissionWindows() calistirildi.");
        CALL_LOGS.get().add("Tool Cagrisi: getOperationalTransmissionWindows() ile Radar Operasyonel Yayin Pencereleri cikarildi.");

        List<TacticalEmissionEntity> all = emissionRepo.findAll();
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

        // 1. 24 Saatlik Zaman Dilimleri (Hourly Buckets: 00:00 - 23:00)
        List<String> saatler = new ArrayList<>();
        int[] dusmanSaatlik = new int[24];
        int[] dostSaatlik = new int[24];
        int[] digerSaatlik = new int[24];
        double[] sureDakikaSaatlik = new double[24];

        for (int h = 0; h < 24; h++) {
            saatler.add(String.format("%02d:00", h));
        }

        // 2. Istatistiksel Toplayicilar
        Map<String, Long> gorevPencereSayisi = new LinkedHashMap<>();
        Map<String, Double> gorevToplamSureDk = new LinkedHashMap<>();
        Map<String, Long> bolgePencereSayisi = new LinkedHashMap<>();
        Map<String, Double> bolgeToplamSureDk = new LinkedHashMap<>();

        List<Map<String, Object>> windowsList = new ArrayList<>();
        double toplamYayinSuresiSn = 0.0;
        int tacizliPencereSayisi = 0;

        for (TacticalEmissionEntity e : all) {
            Instant son = e.getSonTespitZamani() != null ? e.getSonTespitZamani() : Instant.now();
            Instant ilk = e.getIlkTespitZamani() != null ? e.getIlkTespitZamani() : son.minusSeconds(1800);
            double sureSn = e.getSureSn() != null ? e.getSureSn() : 600.0;
            toplamYayinSuresiSn += sureSn;

            boolean taciz = Boolean.TRUE.equals(e.getTaciz());
            if (taciz) {
                tacizliPencereSayisi++;
            }

            int hour = son.atZone(ZoneId.systemDefault()).getHour();
            TeshisKimlik tk = e.getTeshisKimlik();
            if (tk == TeshisKimlik.DUSMAN || tk == TeshisKimlik.MUHTEMEL_DUSMAN) {
                dusmanSaatlik[hour]++;
            } else if (tk == TeshisKimlik.DOST || tk == TeshisKimlik.MUHTEMEL_DOST) {
                dostSaatlik[hour]++;
            } else {
                digerSaatlik[hour]++;
            }
            sureDakikaSaatlik[hour] = Math.round((sureDakikaSaatlik[hour] + (sureSn / 60.0)) * 10.0) / 10.0;

            String gorev = e.getRadarGorevi() != null ? e.getRadarGorevi() : "Takip/Arama";
            gorevPencereSayisi.put(gorev, gorevPencereSayisi.getOrDefault(gorev, 0L) + 1);
            gorevToplamSureDk.put(gorev, Math.round((gorevToplamSureDk.getOrDefault(gorev, 0.0) + (sureSn / 60.0)) * 10.0) / 10.0);

            String yer = e.getHedefYerBilgisi() != null ? e.getHedefYerBilgisi() : "Genel Taktik Saha";
            bolgePencereSayisi.put(yer, bolgePencereSayisi.getOrDefault(yer, 0L) + 1);
            bolgeToplamSureDk.put(yer, Math.round((bolgeToplamSureDk.getOrDefault(yer, 0.0) + (sureSn / 60.0)) * 10.0) / 10.0);

            long windowSpanSn = Math.max(60L, Duration.between(ilk, son).getSeconds());
            double windowSpanDk = Math.round((windowSpanSn / 60.0) * 10.0) / 10.0;
            double aktifSureDk = Math.round((sureSn / 60.0) * 10.0) / 10.0;
            double dolulukYuzde = Math.min(100.0, Math.round(((sureSn / (double) windowSpanSn) * 100.0) * 10.0) / 10.0);

            double pw = e.getMinPwMicroSec() != null ? e.getMinPwMicroSec() : 1.5;
            double pri = (e.getMinPriMicroSec() != null && e.getMinPriMicroSec() > 0) ? e.getMinPriMicroSec() : 1000.0;
            double dutyCycle = Math.round(((pw / pri) * 100.0) * 100.0) / 100.0;

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("id", e.getId());
            w.put("radarAdi", e.getRadarAdi());
            w.put("teshis", tk != null ? tk.name() : "BILINMEYEN");
            w.put("platform", e.getPlatformOrtami() != null ? e.getPlatformOrtami().name() : "KARA");
            w.put("gorev", gorev);
            w.put("hss", e.getHss() != null ? e.getHss() : "-");
            w.put("mevzi", yer);
            w.put("taciz", taciz);
            w.put("pencereBaslangic", timeFmt.format(ilk));
            w.put("pencereBitis", timeFmt.format(son));
            w.put("pencereAraligiDk", windowSpanDk);
            w.put("aktifYayinSuresiDk", aktifSureDk);
            w.put("pencereDolulukYuzde", dolulukYuzde);
            w.put("darbeDutyCycleYuzde", dutyCycle);
            w.put("frekansMhz", e.getMinFrekansMhz());
            windowsList.add(w);
        }

        // Kritik pencereleri aktif yayin suresine ve tacize gore sirala
        windowsList.sort((a, b) -> {
            boolean aTaciz = (boolean) a.get("taciz");
            boolean bTaciz = (boolean) b.get("taciz");
            if (aTaciz && !bTaciz) return -1;
            if (!aTaciz && bTaciz) return 1;
            return Double.compare((double) b.get("aktifYayinSuresiDk"), (double) a.get("aktifYayinSuresiDk"));
        });

        // Taktik harita icin oncelikli hedefleri QUERIED_EMISSIONS'a koy
        for (int i = 0; i < Math.min(30, all.size()); i++) {
            TacticalEmissionEntity e = all.get(i);
            QUERIED_EMISSIONS.get().put(e.getId(), toSummaryDto(e));
        }

        // Tepe saatleri tespit et (En yuksek yayin suresi olan 3 saat)
        List<Integer> hourIndices = new ArrayList<>();
        for (int i = 0; i < 24; i++) hourIndices.add(i);
        hourIndices.sort((a, b) -> Double.compare(sureDakikaSaatlik[b], sureDakikaSaatlik[a]));

        List<String> tepeSaatler = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int h = hourIndices.get(i);
            tepeSaatler.add(String.format("%02d:00 - %02d:00 (Toplam %.1f dk RF yayini, %d radar)", 
                h, (h + 1) % 24, sureDakikaSaatlik[h], dusmanSaatlik[h] + dostSaatlik[h] + digerSaatlik[h]));
        }

        Map<String, Object> saatlikData = new LinkedHashMap<>();
        saatlikData.put("saatler", saatler);
        saatlikData.put("dusmanRadarSayisi", dusmanSaatlik);
        saatlikData.put("dostRadarSayisi", dostSaatlik);
        saatlikData.put("digerRadarSayisi", digerSaatlik);
        saatlikData.put("toplamYayinSuresiDakika", sureDakikaSaatlik);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("toplamIncelenenRadarPenceresi", all.size());
        res.put("toplamYayinSuresiSaat", Math.round((toplamYayinSuresiSn / 3600.0) * 10.0) / 10.0);
        res.put("ortalamaPencereSuresiDk", Math.round(((toplamYayinSuresiSn / all.size()) / 60.0) * 10.0) / 10.0);
        res.put("tacizYapanPencereSayisi", tacizliPencereSayisi);
        res.put("saatlikAnaliz", saatlikData);
        res.put("tepeAktivitePencereleri", tepeSaatler);
        res.put("gorevBazliPencereSureleri", gorevToplamSureDk);
        res.put("bolgeselPencereDagilimi", bolgeToplamSureDk);
        res.put("enKritikYayinPencereleri", windowsList.stream().limit(12).collect(Collectors.toList()));
        res.put("doktrinDegerlendirmesi", "Sahada " + tacizliPencereSayisi + " radar aktif taciz ve kilit maksatli agresif yayin penceresi acmistir. En yogun yayin pencereleri saatlik RF yukunde tepe yapmaktadir. Bu pencereler karsi-tedbir ve SEAD gorev planlamasinda hedef alinmalidir.");

        return res;
    }
}






