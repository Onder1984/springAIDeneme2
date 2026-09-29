package com.c2.ew.infrastructure.repository;

import com.c2.ew.domain.model.EmitterActivity;
import com.c2.ew.domain.model.EmitterFix;
import com.c2.ew.domain.model.EmitterLob;
import com.c2.ew.domain.model.GeoPoint;
import com.c2.ew.domain.model.PagedResult;
import com.c2.ew.infrastructure.entity.EmitterFixEntity;
import com.c2.ew.infrastructure.entity.EmitterLobEntity;
import com.c2.ew.util.GeoUtils;
import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
@Primary
public class PostgreSqlTacticalEmissionRepository implements TacticalEmissionRepository {

    private final EmitterFixJpaRepository fixJpaRepository;
    private final EmitterLobJpaRepository lobJpaRepository;

    public PostgreSqlTacticalEmissionRepository(EmitterFixJpaRepository fixJpaRepository,
                                               EmitterLobJpaRepository lobJpaRepository) {
        this.fixJpaRepository = fixJpaRepository;
        this.lobJpaRepository = lobJpaRepository;
    }

    @Override
    public List<EmitterFix> findFixes(
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
        Instant cutoff = (lastMinutes != null && lastMinutes > 0)
            ? Instant.now().minus(Duration.ofMinutes(lastMinutes))
            : null;

        List<EmitterFixEntity> entities = fixJpaRepository.findAll(
            PageRequest.of(0, 500, Sort.by("threatLevel").descending().and(Sort.by("lastSeen").descending()))
        ).getContent();

        return entities.stream()
            .filter(f -> status == null || f.getStatus() == status)
            .filter(f -> band == null || f.getBand().equalsIgnoreCase(band))
            .filter(f -> minFrequencyMhz == null || f.getFrequencyMhz() >= minFrequencyMhz)
            .filter(f -> maxFrequencyMhz == null || f.getFrequencyMhz() <= maxFrequencyMhz)
            .filter(f -> maxSemiMajorAxisMeters == null || f.getSemiMajorAxisMeters() <= maxSemiMajorAxisMeters)
            .filter(f -> minSemiMajorAxisMeters == null || f.getSemiMajorAxisMeters() >= minSemiMajorAxisMeters)
            .filter(f -> cutoff == null || f.getLastSeen().isAfter(cutoff))
            .filter(f -> {
                if (centerLat == null || centerLon == null || radiusKm == null) {
                    return true;
                }
                double distMeters = GeoUtils.haversineDistanceMeters(
                    new GeoPoint(centerLat, centerLon),
                    new GeoPoint(f.getLatitude(), f.getLongitude())
                );
                return distMeters <= (radiusKm * 1000.0);
            })
            .map(EmitterFixEntity::toDomain)
            .toList();
    }

    private String cleanParam(String val) {
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

    @Override
    public PagedResult<EmitterFix> findFixesPaged(
        int page,
        int pageSize,
        EmitterActivity status,
        String band,
        Integer minThreatLevel,
        String radarType,
        String platformType
    ) {
        String cleanBand = cleanParam(band);
        String cleanRadar = cleanParam(radarType);
        String cleanPlatform = cleanParam(platformType);

        PageRequest pageRequest = PageRequest.of(
            Math.max(0, page),
            Math.min(50, Math.max(1, pageSize)),
            Sort.by("threatLevel").descending().and(Sort.by("lastSeen").descending())
        );

        Specification<EmitterFixEntity> spec = buildFixSpecification(status, cleanBand, minThreatLevel, cleanRadar, cleanPlatform);
        Page<EmitterFixEntity> entityPage = fixJpaRepository.findAll(spec, pageRequest);

        List<EmitterFix> domainItems = entityPage.getContent().stream()
            .map(EmitterFixEntity::toDomain)
            .toList();

        String note = String.format("Toplam %d adet kestirim bulundu. LLM bağlamı için sayfa %d/%d (Sayfa Başına %d hedef) getirildi.",
            entityPage.getTotalElements(), entityPage.getNumber() + 1, Math.max(1, entityPage.getTotalPages()), entityPage.getSize());

        return PagedResult.of(domainItems, entityPage.getNumber(), entityPage.getSize(), entityPage.getTotalElements(), note);
    }

    private Specification<EmitterFixEntity> buildFixSpecification(
        EmitterActivity status,
        String cleanBand,
        Integer minThreatLevel,
        String cleanRadar,
        String cleanPlatform
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (cleanBand != null) {
                predicates.add(cb.equal(cb.lower(root.get("band")), cleanBand.toLowerCase()));
            }
            if (minThreatLevel != null && minThreatLevel > 1) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("threatLevel"), minThreatLevel));
            }
            if (cleanRadar != null) {
                predicates.add(cb.like(cb.lower(root.get("radarType")), "%" + cleanRadar.toLowerCase() + "%"));
            }
            if (cleanPlatform != null) {
                predicates.add(cb.equal(cb.lower(root.get("platformType")), cleanPlatform.toLowerCase()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    public List<EmitterFix> findTopThreats(int limit) {
        int safeLimit = Math.min(50, Math.max(1, limit));
        return fixJpaRepository.findTopThreats(PageRequest.of(0, safeLimit)).stream()
            .map(EmitterFixEntity::toDomain)
            .toList();
    }

    @Override
    public List<EmitterLob> findLobs(
        Integer lastMinutes,
        Boolean unassociatedOnly,
        String sensorNodeId,
        String band,
        Double minFrequencyMhz,
        Double maxFrequencyMhz
    ) {
        List<EmitterLobEntity> entities = lobJpaRepository.findAll(
            PageRequest.of(0, 200, Sort.by("timestamp").descending())
        ).getContent();

        return entities.stream()
            .filter(l -> sensorNodeId == null || l.getSensorNodeId().equalsIgnoreCase(sensorNodeId))
            .filter(l -> band == null || l.getBand().equalsIgnoreCase(band))
            .filter(l -> minFrequencyMhz == null || l.getFrequencyMhz() >= minFrequencyMhz)
            .filter(l -> maxFrequencyMhz == null || l.getFrequencyMhz() <= maxFrequencyMhz)
            .filter(l -> {
                if (unassociatedOnly == null || !unassociatedOnly) return true;
                return l.getAssociatedFixId() == null || l.getAssociatedFixId().isBlank();
            })
            .map(EmitterLobEntity::toDomain)
            .toList();
    }

    @Override
    public PagedResult<EmitterLob> findLobsPaged(int page, int pageSize, String associationStatus, String sensorNodeId, String band) {
        String cleanSensor = (sensorNodeId != null && !sensorNodeId.isBlank() && !sensorNodeId.equalsIgnoreCase("ALL") && !sensorNodeId.equalsIgnoreCase("TÜMÜ"))
            ? sensorNodeId.trim() : null;
        String cleanBand = (band != null && !band.isBlank() && !band.equalsIgnoreCase("ALL") && !band.equalsIgnoreCase("TÜMÜ"))
            ? band.trim() : null;
        String cleanStatus = (associationStatus != null && !associationStatus.isBlank() && !associationStatus.equalsIgnoreCase("ALL"))
            ? associationStatus.trim() : null;

        PageRequest pageRequest = PageRequest.of(
            Math.max(0, page),
            Math.min(100, Math.max(1, pageSize)),
            Sort.by("timestamp").descending()
        );

        Specification<EmitterLobEntity> spec = buildLobSpecification(cleanSensor, cleanBand, cleanStatus);
        Page<EmitterLobEntity> entityPage = lobJpaRepository.findAll(spec, pageRequest);

        List<EmitterLob> domainItems = entityPage.getContent().stream()
            .map(EmitterLobEntity::toDomain)
            .toList();

        String statusDesc = "";
        if (cleanStatus != null) {
            String norm = cleanStatus.toUpperCase();
            if (norm.contains("UNASSOC") || norm.contains("YETIM") || norm.contains("ORPHAN")) {
                statusDesc = "yetim (ilişkilendirilmemiş) ";
            } else if (norm.contains("ASSOC") || norm.contains("ILISKI") || norm.contains("İLİŞKİ")) {
                statusDesc = "kestirimlerle ilişkilendirilmiş ";
            }
        }

        String note = String.format("Toplam %d adet %sLOB sinyali tespit edildi. Sayfa %d/%d (Gösterilen: %d adet).",
            entityPage.getTotalElements(), statusDesc, entityPage.getNumber() + 1, Math.max(1, entityPage.getTotalPages()), domainItems.size());

        return PagedResult.of(domainItems, entityPage.getNumber(), entityPage.getSize(), entityPage.getTotalElements(), note);
    }

    private Specification<EmitterLobEntity> buildLobSpecification(String cleanSensor, String cleanBand, String cleanStatus) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (cleanSensor != null) {
                predicates.add(cb.equal(cb.lower(root.get("sensorNodeId")), cleanSensor.toLowerCase()));
            }
            if (cleanBand != null) {
                predicates.add(cb.equal(cb.lower(root.get("band")), cleanBand.toLowerCase()));
            }
            if (cleanStatus != null) {
                String norm = cleanStatus.trim().toUpperCase();
                if (norm.contains("UNASSOC") || norm.contains("YETIM") || norm.contains("ORPHAN")) {
                    predicates.add(cb.or(
                        cb.isNull(root.get("associatedFixId")),
                        cb.equal(root.get("associatedFixId"), "")
                    ));
                } else if (norm.contains("ASSOC") || norm.contains("ILISKI") || norm.contains("İLİŞKİ")) {
                    predicates.add(cb.and(
                        cb.isNotNull(root.get("associatedFixId")),
                        cb.notEqual(root.get("associatedFixId"), "")
                    ));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    public Map<String, Object> countLobs(
        Integer lastMinutes,
        String associationStatus,
        String sensorNodeId,
        String band,
        Double minFrequencyMhz,
        Double maxFrequencyMhz
    ) {
        String cleanSensor = (sensorNodeId != null && !sensorNodeId.isBlank() && !sensorNodeId.equalsIgnoreCase("ALL")) ? sensorNodeId.trim() : null;
        String cleanBand = (band != null && !band.isBlank() && !band.equalsIgnoreCase("ALL")) ? band.trim() : null;
        String cleanStatus = (associationStatus != null && !associationStatus.isBlank() && !associationStatus.equalsIgnoreCase("ALL")) ? associationStatus.trim() : null;

        Specification<EmitterLobEntity> spec = buildLobSpecification(cleanSensor, cleanBand, cleanStatus);
        long totalCount = lobJpaRepository.count(spec);

        Map<String, Long> bySensor = new LinkedHashMap<>();
        for (Object[] row : lobJpaRepository.countGroupBySensorNode()) {
            bySensor.put(String.valueOf(row[0]), (Long) row[1]);
        }

        Map<String, Long> byBand = new LinkedHashMap<>();
        for (Object[] row : lobJpaRepository.countGroupByBand()) {
            byBand.put(String.valueOf(row[0]), (Long) row[1]);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalLobs", totalCount);
        result.put("associationStatus", cleanStatus != null ? cleanStatus : "ALL");
        result.put("lobsBySensor", bySensor);
        result.put("lobsByBand", byBand);
        result.put("sensorContributions", bySensor);
        result.put("status", "SUCCESS");
        return result;
    }

    @Override
    public List<EmitterLob> findContributingLobsForFix(String fixId) {
        if (fixId == null || fixId.isBlank()) return List.of();
        return lobJpaRepository.findAll().stream()
            .filter(l -> fixId.equalsIgnoreCase(l.getAssociatedFixId()))
            .map(EmitterLobEntity::toDomain)
            .toList();
    }

    @Override
    public Optional<EmitterFix> findFixById(String fixId) {
        return fixJpaRepository.findById(fixId).map(EmitterFixEntity::toDomain);
    }

    @Override
    public Optional<EmitterLob> findLobById(String lobId) {
        return lobJpaRepository.findById(lobId).map(EmitterLobEntity::toDomain);
    }

    @Override
    public List<EmitterFix> findAllFixes() {
        return fixJpaRepository.findAll(Sort.by("threatLevel").descending().and(Sort.by("lastSeen").descending())).stream()
            .map(EmitterFixEntity::toDomain)
            .toList();
    }

    @Override
    public List<EmitterLob> findAllLobs() {
        return lobJpaRepository.findAll(PageRequest.of(0, 1000, Sort.by("timestamp").descending())).stream()
            .map(EmitterLobEntity::toDomain)
            .toList();
    }

    @Override
    public Map<String, Object> getOperationalStatistics() {
        long totalFixes = fixJpaRepository.count();
        long activeFixes = fixJpaRepository.countByStatus(EmitterActivity.ACTIVE);
        long intermittentFixes = fixJpaRepository.countByStatus(EmitterActivity.INTERMITTENT);
        long silentFixes = fixJpaRepository.countByStatus(EmitterActivity.SILENT);

        Map<String, Long> fixesByBand = new LinkedHashMap<>();
        for (Object[] row : fixJpaRepository.countGroupByBand()) {
            fixesByBand.put(String.valueOf(row[0]), (Long) row[1]);
        }

        Map<String, Long> fixesByThreat = new LinkedHashMap<>();
        for (Object[] row : fixJpaRepository.countGroupByThreatLevel()) {
            fixesByThreat.put("ThreatLevel_" + row[0], (Long) row[1]);
        }

        long totalLobs = lobJpaRepository.count();
        Map<String, Long> lobsBySensor = new LinkedHashMap<>();
        for (Object[] row : lobJpaRepository.countGroupBySensorNode()) {
            lobsBySensor.put(String.valueOf(row[0]), (Long) row[1]);
        }

        Map<String, Long> lobsByBand = new LinkedHashMap<>();
        for (Object[] row : lobJpaRepository.countGroupByBand()) {
            lobsByBand.put(String.valueOf(row[0]), (Long) row[1]);
        }

        Map<String, Long> fixesByPlatform = new LinkedHashMap<>();
        for (Object[] row : fixJpaRepository.countGroupByPlatformType()) {
            fixesByPlatform.put(String.valueOf(row[0]), (Long) row[1]);
        }

        Map<String, Long> topRadarTypes = new LinkedHashMap<>();
        for (Object[] row : fixJpaRepository.countGroupByRadarType()) {
            topRadarTypes.put(String.valueOf(row[0]), (Long) row[1]);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalFixes", totalFixes);
        stats.put("activeFixes", activeFixes);
        stats.put("intermittentFixes", intermittentFixes);
        stats.put("silentFixes", silentFixes);
        stats.put("fixesByBand", fixesByBand);
        stats.put("bandDistribution", fixesByBand);
        stats.put("fixesByThreatLevel", fixesByThreat);
        stats.put("fixesByPlatform", fixesByPlatform);
        stats.put("topRadarTypes", topRadarTypes);
        stats.put("totalLobs", totalLobs);
        stats.put("lobsBySensor", lobsBySensor);
        stats.put("sensorContributions", lobsBySensor);
        stats.put("lobsByBand", lobsByBand);
        stats.put("orphanLobsCount", lobJpaRepository.countByAssociatedFixIdIsNull());

        return stats;
    }

    @Override
    public List<Map<String, Object>> getTimeFrequencyPoints(
        Integer lastHours,
        String band,
        String platformType,
        String radarType
    ) {
        int hours = (lastHours != null && lastHours > 0) ? Math.min(72, lastHours) : 24;
        Instant cutoff = Instant.now().minus(Duration.ofHours(hours));

        String cleanBand = cleanParam(band);
        String cleanRadar = cleanParam(radarType);
        String cleanPlatform = cleanParam(platformType);

        Specification<EmitterFixEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("lastSeen"), cutoff));
            if (cleanBand != null) {
                predicates.add(cb.equal(cb.lower(root.get("band")), cleanBand.toLowerCase()));
            }
            if (cleanPlatform != null) {
                predicates.add(cb.equal(cb.lower(root.get("platformType")), cleanPlatform.toLowerCase()));
            }
            if (cleanRadar != null) {
                predicates.add(cb.like(cb.lower(root.get("radarType")), "%" + cleanRadar.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<EmitterFixEntity> entities = fixJpaRepository.findAll(spec, Sort.by("lastSeen").ascending());
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Istanbul"));

        List<Map<String, Object>> points = new ArrayList<>();
        for (EmitterFixEntity entity : entities) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("fixId", entity.getFixId());
            p.put("radarType", entity.getRadarType() != null ? entity.getRadarType() : "Bilinmeyen Radar");
            p.put("platformType", entity.getPlatformType() != null ? entity.getPlatformType() : "LAND_MOBILE");
            p.put("band", entity.getBand());
            p.put("frequencyMhz", entity.getFrequencyMhz());
            p.put("threatLevel", entity.getThreatLevel());
            p.put("status", entity.getStatus().name());
            p.put("timeFormatted", timeFmt.format(entity.getLastSeen()));
            p.put("timestamp", entity.getLastSeen().toString());
            points.add(p);
        }
        return points;
    }
}
