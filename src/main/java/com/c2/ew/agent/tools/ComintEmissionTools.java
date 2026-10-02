package com.c2.ew.agent.tools;

import com.c2.ew.agent.dto.PaginationMetadata;
import com.c2.ew.agent.dto.TacticalComintDetailDto;
import com.c2.ew.agent.dto.TacticalComintSummaryDto;
import com.c2.ew.domain.enums.*;
import com.c2.ew.domain.model.MuhabereFrekansEntry;
import com.c2.ew.domain.model.PagedResult;
import com.c2.ew.infrastructure.entity.TacticalComintEntity;
import com.c2.ew.infrastructure.repository.TacticalComintJpaRepository;
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

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class ComintEmissionTools {

    private static final Logger log = LoggerFactory.getLogger(ComintEmissionTools.class);

    private static final ThreadLocal<List<String>> COMINT_CALL_LOGS = ThreadLocal.withInitial(ArrayList::new);
    // state-based pagedToolCalledInTurn

    public static void resetTurnFlags() {
        getActiveState().pagedToolCalledInTurn = false;
    }

    public static void resetTurnFlags(String convId) {
        if (convId != null && !convId.isBlank()) {
            COMINT_SESSION_STATES.computeIfAbsent(convId, k -> new ActiveComintPaginationState()).pagedToolCalledInTurn = false;
        }
    }

        private static final Map<String, ActiveComintPaginationState> COMINT_SESSION_STATES = new ConcurrentHashMap<>();
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

    public static ActiveComintPaginationState getActiveState() {
        return COMINT_SESSION_STATES.computeIfAbsent(getCurrentConversationId(), k -> new ActiveComintPaginationState());
    }

    public static ActiveComintPaginationState getActiveState(String convId) {
        String id = (convId != null && !convId.isBlank()) ? convId : getCurrentConversationId();
        return COMINT_SESSION_STATES.computeIfAbsent(id, k -> new ActiveComintPaginationState());
    }

        public static class ActiveComintPaginationState {
        public volatile boolean pagedToolCalledInTurn = false;
        public String lastTeshis;
        public String lastVeriKaynagi;
        public String lastLisan;
        public String lastTip;
        public String lastHaberlesmeSekli;
        public String lastProtokol;
        public String lastModulasyon;
        public String lastCagriAdi;
        public Boolean lastMti;
        public Double lastMinFrekansMhz;
        public Double lastMaxFrekansMhz;
        public Double lastMinGenlikDbm;
        public Double lastMaxGenlikDbm;

        public int lastComintPage = -1;
        public int comintPageSize = 10;
        public long comintTotalCount = 0;
        public int comintTotalPages = 0;
        public boolean comintHasMore = false;
    }

    public static List<String> drainCallLogs() {
        List<String> logs = new ArrayList<>(COMINT_CALL_LOGS.get());
        COMINT_CALL_LOGS.get().clear();
        return logs;
    }



        public static PaginationMetadata drainPaginationMetadata() {
        return drainPaginationMetadata(getCurrentConversationId());
    }

        public static PaginationMetadata drainPaginationMetadata(String convId) {
        ActiveComintPaginationState state = getActiveState(convId);
        if (!state.pagedToolCalledInTurn) {
            return null;
        }
        state.pagedToolCalledInTurn = false; // Tek seferlik tur bayrağını tüket

        // KATI KISITLANMA KONTROLÜ:
        // Sayfalama SADECE ve SADECE kayıtların belirli bir kısmı gelip kısıtlandığında
        // (totalCount > pageSize) ve arkada gerçekten devam eden kayıt olduğunda (hasMore == true) anlamlıdır!
        if (!state.comintHasMore || state.comintTotalCount <= state.comintPageSize || state.lastComintPage < 0) {
            return null;
        }

        return new PaginationMetadata(
            true,
            state.lastComintPage,
            state.comintTotalPages,
            state.comintTotalCount,
            state.comintPageSize,
            "COMINT_EMISSIONS",
            "Sonraki sayfadaki muhabere hedeflerini listelemek için 'sonraki sayfayı getir' veya 'devamını göster' diyebilirsiniz."
        );
    }

    public static PaginationMetadata getPaginationMetadata() {
        return drainPaginationMetadata();
    }



    private final TacticalComintJpaRepository comintRepo;
    private final ObjectMapper objectMapper;

    public ComintEmissionTools(TacticalComintJpaRepository comintRepo, ObjectMapper objectMapper) {
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
            trimmed.equalsIgnoreCase("HEPSI") ||
            trimmed.equalsIgnoreCase("FARKETMEZ")) {
            return null;
        }
        return trimmed;
    }

    private TeshisKimlik normalizeTeshis(String raw) {
        String clean = cleanParam(raw);
        if (clean == null) return null;
        String upper = clean.toUpperCase(Locale.ENGLISH);

        if (clean.equalsIgnoreCase("D\u00DC\u015EMAN") || upper.contains("DUSMAN") || upper.contains("ENEMY") || upper.contains("D\u00DC\u015E")) {
            if (upper.contains("MUHTEMEL") || upper.contains("PROBABLE") || upper.contains("SUSPECT")) {
                return TeshisKimlik.MUHTEMEL_DUSMAN;
            }
            return TeshisKimlik.DUSMAN;
        }
        if (clean.equalsIgnoreCase("DOST") || upper.contains("DOST") || upper.contains("FRIEND")) {
            if (upper.contains("MUHTEMEL") || upper.contains("PROBABLE")) {
                return TeshisKimlik.MUHTEMEL_DOST;
            }
            return TeshisKimlik.DOST;
        }
        if (upper.contains("TARAFSIZ") || upper.contains("NEUTRAL")) {
            return TeshisKimlik.TARAFSIZ;
        }
        if (upper.contains("BILINMEYEN") || upper.contains("UNKNOWN") || clean.equalsIgnoreCase("B\u0130L\u0130NMEYEN")) {
            return TeshisKimlik.BILINMEYEN;
        }
        try {
            return TeshisKimlik.valueOf(upper);
        } catch (Exception e) {
            return null;
        }
    }

    private MuhabereBantTipi normalizeTip(String raw) {
        String clean = cleanParam(raw);
        if (clean == null) return null;
        String upper = clean.toUpperCase(Locale.ENGLISH);
        if (upper.contains("HF") && !upper.contains("VHF")) return MuhabereBantTipi.HF;
        if (upper.contains("VHF")) return MuhabereBantTipi.VHF;
        try {
            return MuhabereBantTipi.valueOf(upper);
        } catch (Exception e) {
            return null;
        }
    }

    private HaberlesmeSekli normalizeHaberlesmeSekli(String raw) {
        String clean = cleanParam(raw);
        if (clean == null) return null;
        String upper = clean.toUpperCase(Locale.ENGLISH);
        if (upper.contains("KRIPTO") || upper.contains("SIFRE") || upper.contains("\u015E\u0130FRE")) return HaberlesmeSekli.KRIPTO;
        if (upper.contains("DARBE") || upper.contains("BURST") || upper.contains("PATLAMA")) return HaberlesmeSekli.DARBE_PATLAMA;
        if (upper.contains("GAYDA")) return HaberlesmeSekli.KARISTIRMA_GAYDA;
        if (upper.contains("GURULTU") || upper.contains("G\u00DCR\u00DCLT\u00DC") || upper.contains("NOISE")) return HaberlesmeSekli.KARISTIRMA_GURULTU;
        if (upper.contains("VERI") || upper.contains("DATA") || upper.contains("VER\u0130")) return HaberlesmeSekli.VERI;
        if (upper.contains("SES") || upper.contains("VOICE") || upper.contains("AUDIO")) return HaberlesmeSekli.SES;
        try {
            return HaberlesmeSekli.valueOf(upper);
        } catch (Exception e) {
            return null;
        }
    }

        @Tool(description = "Sahadaki tum taktik muhabere emisyonlarinin makro ozet istatistiklerini doner. getComintMacroStats ile aynidir.")
    public Map<String, Object> queryComintMacroStats() {
        return getComintMacroStats();
    }

    @Tool(description = "Sahadaki tum taktik muhabere emisyonlarinin makro ozet istatistiklerini doner.")
    public Map<String, Object> getComintMacroStats() {
        COMINT_CALL_LOGS.get().add("Tool Çağrısı: getComintMacroStats() çalıştırıldı.");
        Map<String, Object> stats = new LinkedHashMap<>();

        long totalCount = comintRepo.count();
        stats.put("toplamMuhabereKaydi", totalCount);
        stats.put("dusmanMuhabereSayisi", comintRepo.countByTeshisKimlik(TeshisKimlik.DUSMAN));
        stats.put("kriptoluYayinSayisi", comintRepo.countByHaberlesmeSekli(HaberlesmeSekli.KRIPTO));
        stats.put("darbePatlamaSayisi", comintRepo.countByHaberlesmeSekli(HaberlesmeSekli.DARBE_PATLAMA));
        stats.put("karistirmaGaydaSayisi", comintRepo.countByHaberlesmeSekli(HaberlesmeSekli.KARISTIRMA_GAYDA));
        stats.put("karistirmaGurultuSayisi", comintRepo.countByHaberlesmeSekli(HaberlesmeSekli.KARISTIRMA_GURULTU));
        stats.put("hfBantSayisi", comintRepo.countByTip(MuhabereBantTipi.HF));
        stats.put("vhfBantSayisi", comintRepo.countByTip(MuhabereBantTipi.VHF));

        stats.put("teshisDagilimi", mapDistribution(comintRepo.getTeshisDistribution()));
        stats.put("haberlesmeSekliDagilimi", mapDistribution(comintRepo.getHaberlesmeSekliDistribution()));
        stats.put("tipDagilimi", mapDistribution(comintRepo.getTipDistribution()));
        stats.put("lisanDagilimi", mapDistribution(comintRepo.getLisanDistribution()));
        stats.put("veriKaynagiDagilimi", mapDistribution(comintRepo.getVeriKaynagiDistribution()));

        return stats;
    }

    @Tool(description = "Taktik sahada tespit edilen öncelikli ve kritik düşman muhabere yayınlarını listeler (Düşman, Kriptolu, Darbe Patlama / Burst veya Karıştırma uygulayan kritik telsiz ağları).")
    public List<TacticalComintSummaryDto> queryPriorityComintThreats(Integer limit) {
        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 50) : 15;
        COMINT_CALL_LOGS.get().add(String.format("Tool Çağrısı: queryPriorityComintThreats(limit=%d) çalıştırıldı.", effectiveLimit));

        Specification<TacticalComintEntity> spec = (root, query, cb) -> {
            Predicate isDusman = cb.equal(root.get("teshisKimlik"), TeshisKimlik.DUSMAN);
            Predicate isMuhtemelDusman = cb.equal(root.get("teshisKimlik"), TeshisKimlik.MUHTEMEL_DUSMAN);
            Predicate isKripto = cb.equal(root.get("haberlesmeSekli"), HaberlesmeSekli.KRIPTO);
            Predicate isDarbe = cb.equal(root.get("haberlesmeSekli"), HaberlesmeSekli.DARBE_PATLAMA);
            Predicate isGayda = cb.equal(root.get("haberlesmeSekli"), HaberlesmeSekli.KARISTIRMA_GAYDA);
            Predicate isGurultu = cb.equal(root.get("haberlesmeSekli"), HaberlesmeSekli.KARISTIRMA_GURULTU);

            return cb.or(isDusman, isMuhtemelDusman, isKripto, isDarbe, isGayda, isGurultu);
        };

        Page<TacticalComintEntity> page = comintRepo.findAll(spec, PageRequest.of(0, effectiveLimit, Sort.by(Sort.Direction.DESC, "sonTespitZamani")));
        List<TacticalComintSummaryDto> dtos = page.getContent().stream().map(this::toSummaryDto).collect(Collectors.toList());

        for (TacticalComintSummaryDto dto : dtos) {
        }
        return dtos;
    }

    @Tool(description = "Muhabere (telsiz/haberleşme) yayınlarını sayfalı ve detaylı filtrelerle sorgular. Frekans aralığı (minFrekansMhz - maxFrekansMhz aralık kesişimi), teşhis (DÜŞMAN, DOST vb.), lisan (Türkçe, Rusça, Arapça...), tip (HF, VHF), haberleşme şekli (SES, KRIPTO, DARBE_PATLAMA, GAYDA vb.), protokol, çağrı adı ve sayfalama destekler.")
    public PagedResult<TacticalComintSummaryDto> queryComintEmissionsPaged(
        String teshisKimlik,
        String veriKaynagi,
        String lisan,
        String tip,
        String haberlesmeSekli,
        String protokol,
        String modulasyon,
        String cagriAdi,
        Boolean mti,
        Double minFrekansMhz,
        Double maxFrekansMhz,
        Double minGenlikDbm,
        Double maxGenlikDbm,
        Integer sayfaNo,
        Integer sayfaBoyutu
    ) {
        String cleanTeshis = cleanParam(teshisKimlik);
        String cleanKaynak = cleanParam(veriKaynagi);
        String cleanLisan = cleanParam(lisan);
        String cleanTip = cleanParam(tip);
        String cleanHaberlesme = cleanParam(haberlesmeSekli);
        String cleanProtokol = cleanParam(protokol);
        String cleanModulasyon = cleanParam(modulasyon);
        String cleanCagri = cleanParam(cagriAdi);

        // Dummy LLM aralıklarını filtrele
        Double effMinFreq = (minFrekansMhz != null && minFrekansMhz <= 0.0 && (maxFrekansMhz == null || maxFrekansMhz >= 100000.0)) ? null : minFrekansMhz;
        Double effMaxFreq = (maxFrekansMhz != null && maxFrekansMhz >= 100000.0 && (minFrekansMhz == null || minFrekansMhz <= 0.0)) ? null : maxFrekansMhz;

        int pageIdx = (sayfaNo == null || sayfaNo <= 0) ? 0 : sayfaNo - 1;
        int pageSize = (sayfaBoyutu != null && sayfaBoyutu > 0) ? Math.min(sayfaBoyutu, 50) : 10;

        ActiveComintPaginationState state = getActiveState();
        state.lastTeshis = cleanTeshis;
        state.lastVeriKaynagi = cleanKaynak;
        state.lastLisan = cleanLisan;
        state.lastTip = cleanTip;
        state.lastHaberlesmeSekli = cleanHaberlesme;
        state.lastProtokol = cleanProtokol;
        state.lastModulasyon = cleanModulasyon;
        state.lastCagriAdi = cleanCagri;
        state.lastMti = mti;
        state.lastMinFrekansMhz = effMinFreq;
        state.lastMaxFrekansMhz = effMaxFreq;
        state.lastMinGenlikDbm = minGenlikDbm;
        state.lastMaxGenlikDbm = maxGenlikDbm;
        state.lastComintPage = pageIdx;
        state.comintPageSize = pageSize;

        state.pagedToolCalledInTurn = true;
        log.info("queryComintEmissionsPaged called with teshis={}, lisan={}, tip={}, freq=[{}-{}], page={}",
            cleanTeshis, cleanLisan, cleanTip, effMinFreq, effMaxFreq, pageIdx);

        COMINT_CALL_LOGS.get().add(String.format("Tool: queryComintEmissionsPaged(teshis=%s, lisan=%s, tip=%s, haberlesme=%s, freq=[%s-%s], page=%d)",
            cleanTeshis, cleanLisan, cleanTip, cleanHaberlesme, effMinFreq, effMaxFreq, pageIdx));

        Specification<TacticalComintEntity> spec = createComintSpecification(
            cleanTeshis, cleanKaynak, cleanLisan, cleanTip, cleanHaberlesme, cleanProtokol, cleanModulasyon, cleanCagri, mti,
            effMinFreq, effMaxFreq, minGenlikDbm, maxGenlikDbm
        );

        Page<TacticalComintEntity> resultPage = comintRepo.findAll(spec, PageRequest.of(pageIdx, pageSize, Sort.by(Sort.Direction.DESC, "sonTespitZamani")));

        state.comintTotalCount = resultPage.getTotalElements();
        state.comintTotalPages = resultPage.getTotalPages();
        state.comintHasMore = resultPage.hasNext();

        List<TacticalComintSummaryDto> dtos = resultPage.getContent().stream().map(this::toSummaryDto).collect(Collectors.toList());

        for (TacticalComintSummaryDto dto : dtos) {
        }

        String note = String.format("Sayfa %d / %d (Toplam %d muhabere yayını)", pageIdx + 1, resultPage.getTotalPages(), resultPage.getTotalElements());
        return PagedResult.of(dtos, pageIdx, pageSize, resultPage.getTotalElements(), note);
    }

    @Tool(description = "Operatör 'sonraki sayfayı ver', 'bana sonraki sayfayı da ver', 'devamını getir' dediğinde bir önceki sorgu filtrelerini (frekans, lisan, tip, teşhis vb.) hatırlayarak sonraki sayfayı çeker.")
    public PagedResult<TacticalComintSummaryDto> getNextPageOfComintEmissions() {
        ActiveComintPaginationState state = getActiveState();
        int targetPage = (state.lastComintPage < 0) ? 1 : state.lastComintPage + 2; // 1-indexed

        COMINT_CALL_LOGS.get().add(String.format("Tool Çağrısı: getNextPageOfComintEmissions() çalıştırıldı -> Sayfa: %d", targetPage));
        return queryComintEmissionsPaged(
            state.lastTeshis,
            state.lastVeriKaynagi,
            state.lastLisan,
            state.lastTip,
            state.lastHaberlesmeSekli,
            state.lastProtokol,
            state.lastModulasyon,
            state.lastCagriAdi,
            state.lastMti,
            state.lastMinFrekansMhz,
            state.lastMaxFrekansMhz,
            state.lastMinGenlikDbm,
            state.lastMaxGenlikDbm,
            targetPage,
            state.comintPageSize
        );
    }

    @Tool(description = "Operatör 'önceki sayfaya dön', 'geri git' dediğinde bir önceki sayfayı çeker.")
    public PagedResult<TacticalComintSummaryDto> getPrevPageOfComintEmissions() {
        ActiveComintPaginationState state = getActiveState();
        int targetPage = Math.max(1, (state.lastComintPage < 0) ? 1 : state.lastComintPage); // 1-indexed

        COMINT_CALL_LOGS.get().add(String.format("Tool Çağrısı: getPrevPageOfComintEmissions() çalıştırıldı -> Sayfa: %d", targetPage));
        return queryComintEmissionsPaged(
            state.lastTeshis,
            state.lastVeriKaynagi,
            state.lastLisan,
            state.lastTip,
            state.lastHaberlesmeSekli,
            state.lastProtokol,
            state.lastModulasyon,
            state.lastCagriAdi,
            state.lastMti,
            state.lastMinFrekansMhz,
            state.lastMaxFrekansMhz,
            state.lastMinGenlikDbm,
            state.lastMaxGenlikDbm,
            targetPage,
            state.comintPageSize
        );
    }

        @Tool(description = "Haberleşme ağında en çok konuşan, en aktif telsiz çağrı adlarının (Callsign) tespit sayılarını ve sıralamasını döner. 'En çok hangi çağrı adı konuştu?', 'En aktif telsizler kimler?' gibi analiz soruları için doğrudan bu araç çağrılmalıdır.")
    public Map<String, Long> getMostActiveCallsigns(Integer limit) {
        int max = (limit == null || limit <= 0) ? 10 : Math.min(limit, 30);
        COMINT_CALL_LOGS.get().add(String.format("Tool çağrısı: getMostActiveCallsigns(limit=%d) çalıştırıldı.", max));
        Map<String, Long> result = new LinkedHashMap<>();
        List<Object[]> rows = comintRepo.getTopCallsignsDistribution();
        for (int i = 0; i < Math.min(max, rows.size()); i++) {
            Object[] row = rows.get(i);
            result.put(String.valueOf(row[0]), (Long) row[1]);
        }
        return result;
    }

    @Tool(description = "Telsiz çağrı adı (Callsign) bazlı link ve haberleşme ağı analizi yapar. Verilen çağrı adının konuştuğu karşı çağrı adlarını (karsiCagriAdi) ve tüm ilişkili telsiz yayınlarını getirir.")
    public List<TacticalComintSummaryDto> queryComintCallsignNetwork(String cagriAdi) {
        String clean = cleanParam(cagriAdi);
        if (clean == null) return List.of();
        COMINT_CALL_LOGS.get().add(String.format("Tool Çağrısı: queryComintCallsignNetwork(cagriAdi=%s) çalıştırıldı.", clean));

        List<TacticalComintEntity> list = comintRepo.findByCagriAdiContainingIgnoreCaseOrKarsiCagriAdiContainingIgnoreCase(clean, clean);
        List<TacticalComintSummaryDto> dtos = list.stream().map(this::toSummaryDto).collect(Collectors.toList());

        for (TacticalComintSummaryDto dto : dtos) {
        }
        return dtos;
    }

    @Tool(description = "Tekil bir muhabere yayınının (COM-XXXX) 30 parametresinin tamamını (frekans listesi, çağrı adları, kripto, operatör notu, onaylayan kullanıcı vb.) döner.")
    public TacticalComintDetailDto getComintDetails(String comintId) {
        String cleanId = cleanParam(comintId);
        if (cleanId == null) return null;
        COMINT_CALL_LOGS.get().add(String.format("Tool Çağrısı: getComintDetails(comintId=%s) çalıştırıldı.", cleanId));

        return comintRepo.findById(cleanId).map(this::toDetailDto).orElse(null);
    }

    private Specification<TacticalComintEntity> createComintSpecification(
        String teshis, String kaynak, String lisan, String tip, String haberlesme, String protokol, String modulasyon, String cagri, Boolean mti,
        Double minFreq, Double maxFreq, Double minGenlik, Double maxGenlik
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (teshis != null) {
                TeshisKimlik normTeshis = normalizeTeshis(teshis);
                if (normTeshis != null) {
                    predicates.add(cb.equal(root.get("teshisKimlik"), normTeshis));
                }
            }

            if (kaynak != null) {
                try {
                    predicates.add(cb.equal(root.get("veriKaynagi"), VeriKaynagi.valueOf(kaynak.toUpperCase())));
                } catch (Exception ignored) {}
            }

            if (lisan != null) {
                predicates.add(cb.like(cb.lower(root.get("lisan")), "%" + lisan.toLowerCase(Locale.ENGLISH) + "%"));
            }

            if (tip != null) {
                MuhabereBantTipi normTip = normalizeTip(tip);
                if (normTip != null) {
                    predicates.add(cb.equal(root.get("tip"), normTip));
                }
            }

            if (haberlesme != null) {
                HaberlesmeSekli normHaberlesme = normalizeHaberlesmeSekli(haberlesme);
                if (normHaberlesme != null) {
                    predicates.add(cb.equal(root.get("haberlesmeSekli"), normHaberlesme));
                }
            }

            if (protokol != null) {
                try {
                    predicates.add(cb.equal(root.get("protokol"), MuhabereProtokol.valueOf(protokol.toUpperCase())));
                } catch (Exception ignored) {}
            }

            if (modulasyon != null) {
                try {
                    predicates.add(cb.equal(root.get("modulasyon"), MuhabereModulasyon.valueOf(modulasyon.toUpperCase())));
                } catch (Exception ignored) {}
            }

            if (cagri != null) {
                Predicate byCagri = cb.like(cb.lower(root.get("cagriAdi")), "%" + cagri.toLowerCase() + "%");
                Predicate byKarsi = cb.like(cb.lower(root.get("karsiCagriAdi")), "%" + cagri.toLowerCase() + "%");
                predicates.add(cb.or(byCagri, byKarsi));
            }

            if (mti != null) {
                predicates.add(cb.equal(root.get("mti"), mti));
            }

            // Interval Overlap (Aralık Kesişimi) Sorguları:
            // Hedefin [minFrekansMhz, maxFrekansMhz] aralığı kullanıcının aradığı [minFreq, maxFreq] ile kesişmeli
            if (minFreq != null && maxFreq != null) {
                predicates.add(cb.and(
                    cb.lessThanOrEqualTo(root.get("minFrekansMhz"), maxFreq),
                    cb.greaterThanOrEqualTo(root.get("maxFrekansMhz"), minFreq)
                ));
            } else if (minFreq != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxFrekansMhz"), minFreq));
            } else if (maxFreq != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("minFrekansMhz"), maxFreq));
            }

            if (minGenlik != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("genlikDbm"), minGenlik));
            }
            if (maxGenlik != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("genlikDbm"), maxGenlik));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public TacticalComintSummaryDto toSummaryDto(TacticalComintEntity e) {
        return toStaticDto(e);
    }

    public static TacticalComintSummaryDto toStaticDto(TacticalComintEntity e) {
        return new TacticalComintSummaryDto(
            e.getId(),
            e.getVeriKaynagi() != null ? e.getVeriKaynagi().name() : null,
            e.getKuvvetSiraNo(),
            e.getGoreviIcraEdenUstBirlik(),
            e.getGoreviIcraEdenEhUnsuru(),
            e.getYon(),
            e.getEhUnsuruEnlem(),
            e.getEhUnsuruBoylam(),
            e.getYayinEnlem(),
            e.getYayinBoylam(),
            e.getYayinSemiMajorMeters(),
            e.getYayinSemiMinorMeters(),
            e.getYayinOrientationDegrees(),
            e.getHedefYerBilgisi(),
            e.getHedefKaynagi() != null ? e.getHedefKaynagi().name() : null,
            e.getLisan(),
            e.getProtokol() != null ? e.getProtokol().name() : null,
            e.getBantGenisligiHz(),
            e.getModulasyon() != null ? e.getModulasyon().name() : null,
            e.getMti(),
            e.getTip() != null ? e.getTip().name() : null,
            e.getGenlikDbm(),
            e.getHaberlesmeSekli() != null ? e.getHaberlesmeSekli().name() : null,
            e.getCalismaSekli() != null ? e.getCalismaSekli().name() : null,
            e.getAltEsikSeviyesiDbm(),
            e.getCagriAdi(),
            e.getKarsiCagriAdi(),
            e.getSonTespitZamani(),
            e.getSureSn(),
            e.getTeshisKimlik() != null ? e.getTeshisKimlik().name() : null,
            e.getMinFrekansMhz(),
            e.getMaxFrekansMhz(),
            e.getOperatorNotu(),
            e.getOnaylayanKullanici(),
            e.getOnayNotu()
        );
    }

    private TacticalComintDetailDto toDetailDto(TacticalComintEntity e) {
        List<MuhabereFrekansEntry> frekansList = Collections.emptyList();
        try {
            if (e.getFrekansListesiJson() != null && !e.getFrekansListesiJson().isBlank()) {
                frekansList = objectMapper.readValue(e.getFrekansListesiJson(), new TypeReference<List<MuhabereFrekansEntry>>() {});
            }
        } catch (Exception ex) {
            log.warn("Frekans JSON parse hatası [{}]: {}", e.getId(), ex.getMessage());
        }

        return new TacticalComintDetailDto(
            e.getId(),
            e.getVeriKaynagi() != null ? e.getVeriKaynagi().name() : null,
            e.getKuvvetSiraNo(),
            e.getGoreviIcraEdenUstBirlik(),
            e.getGoreviIcraEdenEhUnsuru(),
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
            e.getHedefKaynagi() != null ? e.getHedefKaynagi().name() : null,
            e.getVeriGirisiYapanBirlik(),
            e.getLisan(),
            e.getProtokol() != null ? e.getProtokol().name() : null,
            e.getBantGenisligiHz(),
            e.getModulasyon() != null ? e.getModulasyon().name() : null,
            e.getMti(),
            e.getTip() != null ? e.getTip().name() : null,
            e.getGenlikDbm(),
            e.getHaberlesmeSekli() != null ? e.getHaberlesmeSekli().name() : null,
            e.getCalismaSekli() != null ? e.getCalismaSekli().name() : null,
            e.getAltEsikSeviyesiDbm(),
            e.getCagriAdi(),
            e.getKarsiCagriAdi(),
            e.getIlkTespitZamani(),
            e.getSonTespitZamani(),
            e.getSureSn(),
            e.getTeshisKimlik() != null ? e.getTeshisKimlik().name() : null,
            e.getMinFrekansMhz(),
            e.getMaxFrekansMhz(),
            frekansList,
            e.getOperatorNotu(),
            e.getOnaylayanKullanici(),
            e.getOnayNotu()
        );
    }

    @Tool(description = "Sahadaki tum muhabere aginin dugum (telsiz cagri adlari) ve kenar (karsi cagri adi iletisimi) topolojisini, ag merkeziligini (hub/komuta merkezi) ve ECharts uyumlu network grafigini cikarir.")
    public Map<String, Object> getComintNetworkTopologyGraph(Integer limit) {
        log.info("Tool Cagrisi: getComintNetworkTopologyGraph(limit={}) calistirildi.", limit);
        COMINT_CALL_LOGS.get().add("Tool Cagrisi: getComintNetworkTopologyGraph() ile telsiz haberlesme ag topolojisi cikarildi.");

        List<Object[]> matrix = comintRepo.getCallsignInteractionMatrix();
        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, Integer> nodeDegrees = new HashMap<>();
        Map<String, String> nodePrimaryLang = new HashMap<>();
        Map<String, String> nodePrimaryProto = new HashMap<>();
        Map<String, String> nodePrimaryBand = new HashMap<>();

        List<Map<String, Object>> links = new ArrayList<>();

        if (matrix != null) {
            for (Object[] row : matrix) {
                String src = row[0] != null ? row[0].toString().trim() : null;
                String tgt = row[1] != null ? row[1].toString().trim() : null;
                long count = row[2] != null ? ((Number) row[2]).longValue() : 1L;
                String lang = row[3] != null ? row[3].toString() : "Bilinmiyor";
                String proto = row[4] != null ? row[4].toString() : "Standart";
                String band = (row.length > 5 && row[5] != null) ? row[5].toString() : "VHF";

                if (src != null && !src.isEmpty() && tgt != null && !tgt.isEmpty()) {
                    nodeDegrees.put(src, nodeDegrees.getOrDefault(src, 0) + (int) count);
                    nodeDegrees.put(tgt, nodeDegrees.getOrDefault(tgt, 0) + (int) count);
                    nodePrimaryLang.putIfAbsent(src, lang);
                    nodePrimaryProto.putIfAbsent(src, proto);
                    nodePrimaryBand.putIfAbsent(src, band);
                    nodePrimaryBand.putIfAbsent(tgt, band);

                    Map<String, Object> link = new LinkedHashMap<>();
                    link.put("source", src);
                    link.put("target", tgt);
                    link.put("weight", count);
                    link.put("protocol", proto);
                    link.put("language", lang);
                    link.put("band", band);
                    links.add(link);
                }
            }
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> topHubs = new ArrayList<>();

        List<Map.Entry<String, Integer>> sortedNodes = new ArrayList<>(nodeDegrees.entrySet());
        sortedNodes.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        int rank = 0;
        for (Map.Entry<String, Integer> entry : sortedNodes) {
            rank++;
            String name = entry.getKey();
            int degree = entry.getValue();

            boolean isHQ = name.contains("HQ") || name.contains("KOMUTA") || name.contains("MERKEZ") || name.contains("BASE") || degree >= 20;
            boolean isRelay = name.contains("RELAY") || name.contains("ROLE") || name.contains("BAZ");
            boolean isJammer = name.contains("JAM") || name.contains("NOISE") || name.contains("ELECTRON");

            String category;
            String role;
            String color;
            if (isHQ) {
                category = "Komuta/Master Hub";
                role = "HUB";
                color = "#c95d63";
            } else if (isRelay) {
                category = "Röle/Link İstasyonu";
                role = "RELAY";
                color = "#d4a373";
            } else if (isJammer) {
                category = "Elektronik Taarruz/Jammer";
                role = "JAMMING";
                color = "#9d8ec2";
            } else {
                category = "Taktik Saha İstasyonu";
                role = "STATION";
                color = "#5fa8d3";
            }

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", name);
            node.put("name", name);
            node.put("category", category);
            node.put("role", role);
            node.put("color", color);
            node.put("symbolSize", isHQ ? 50 : (isRelay ? 38 : (isJammer ? 36 : Math.max(20, Math.min(32, 16 + degree)))));
            node.put("value", degree);
            node.put("language", nodePrimaryLang.getOrDefault(name, "-"));
            node.put("protocol", nodePrimaryProto.getOrDefault(name, "-"));
            node.put("band", nodePrimaryBand.getOrDefault(name, "VHF"));
            nodes.add(node);

            if (isHQ) {
                Map<String, Object> hubInfo = new LinkedHashMap<>();
                hubInfo.put("callsign", name);
                hubInfo.put("totalInteractions", degree);
                hubInfo.put("language", nodePrimaryLang.getOrDefault(name, "-"));
                hubInfo.put("protocol", nodePrimaryProto.getOrDefault(name, "-"));
                hubInfo.put("band", nodePrimaryBand.getOrDefault(name, "VHF"));
                hubInfo.put("roleEstimate", "Merkez Komuta ve Karargah (HQ)");
                topHubs.add(hubInfo);
            }
        }

        result.put("toplamIstasyonSayisi", nodes.size());
        result.put("toplamIletisimLinkiSayisi", links.size());
        result.put("tespitEdilenKomutaMerkezleri", topHubs);
        result.put("nodes", nodes);
        result.put("links", links);
        result.put("taktikOzet", "Sahada " + nodes.size() + " farkli telsiz cagri adi arasinda " + links.size() + " aktif haberlesme linki cikarilmistir. En kritik ag merkezleri: " +
            topHubs.stream().map(h -> h.get("callsign") + " (" + h.get("totalInteractions") + " iletisim)").reduce((a, b) -> a + ", " + b).orElse("Yok"));

        return result;
    }

    @Tool(description = "Haberlesme guvenligi (COMSEC) ve taktik telsiz emarelerini analiz eder: Kriptolu gorusmeler, darbe patlama (burst), telsiz karistirma yayinlari ve MTI (hareketli telsiz) durumunu raporlar.")
    public Map<String, Object> getComsecAndTransmissionTactics() {
        log.info("Tool Cagrisi: getComsecAndTransmissionTactics() calistirildi.");
        COMINT_CALL_LOGS.get().add("Tool Cagrisi: getComsecAndTransmissionTactics() calistirildi.");

        Map<String, Object> res = new LinkedHashMap<>();
        long kripto = comintRepo.countByHaberlesmeSekli(HaberlesmeSekli.KRIPTO);
        long burst = comintRepo.countByHaberlesmeSekli(HaberlesmeSekli.DARBE_PATLAMA);
        long gayda = comintRepo.countByHaberlesmeSekli(HaberlesmeSekli.KARISTIRMA_GAYDA);
        long gurultu = comintRepo.countByHaberlesmeSekli(HaberlesmeSekli.KARISTIRMA_GURULTU);
        long total = comintRepo.count();

        res.put("toplamMuhabereKaydi", total);
        res.put("kriptoluYayinSayisi", kripto);
        res.put("darbePatlamaBurstSayisi", burst);
        res.put("karistirmaGaydaSayisi", gayda);
        res.put("karistirmaGurultuSayisi", gurultu);
        res.put("kriptoOraniYuzde", total > 0 ? Math.round((kripto * 100.0) / total) : 0);
        res.put("taktikEmare", burst > 0 ? "Darbe Patlama (Burst) tespit edildi! Dusman acil taktik veri/hedef aktarimi yapiyor olabilir." : "Standart kriptolu ve ses trafigi mevcut.");

        return res;
    }

    private Map<String, Long> mapDistribution(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        if (rows != null) {
            for (Object[] r : rows) {
                if (r[0] != null) {
                    map.put(r[0].toString(), ((Number) r[1]).longValue());
                }
            }
        }
        return map;
    }
}

