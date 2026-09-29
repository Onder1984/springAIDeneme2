package com.c2.ew.infrastructure.repository;

import com.c2.ew.domain.model.EmitterActivity;
import com.c2.ew.domain.model.EmitterFix;
import com.c2.ew.domain.model.EmitterLob;
import com.c2.ew.domain.model.PagedResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Taktik Elektronik Harp Yayın ve Kestirim Veri Deposu Arayüzü (PostgreSQL Destekli)
 */
public interface TacticalEmissionRepository {

    default List<EmitterFix> findFixes(
        Double centerLat,
        Double centerLon,
        Double radiusKm,
        Integer lastMinutes,
        EmitterActivity status,
        String band
    ) {
        return findFixes(centerLat, centerLon, radiusKm, lastMinutes, status, band, null, null, null, null);
    }

    List<EmitterFix> findFixes(
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
    );

    /**
     * Güvenli Sayfalama: LLM context penceresini koruyarak kestirimleri sayfalı olarak döner.
     */
    default PagedResult<EmitterFix> findFixesPaged(
        int page,
        int pageSize,
        EmitterActivity status,
        String band,
        Integer minThreatLevel
    ) {
        return findFixesPaged(page, pageSize, status, band, minThreatLevel, null, null);
    }

    PagedResult<EmitterFix> findFixesPaged(
        int page,
        int pageSize,
        EmitterActivity status,
        String band,
        Integer minThreatLevel,
        String radarType,
        String platformType
    );

    List<Map<String, Object>> getTimeFrequencyPoints(
        Integer lastHours,
        String band,
        String platformType,
        String radarType
    );

    /**
     * En yüksek tehdit seviyesine ve en güncel görülme zamanına göre ilk K hedefi döner.
     */
    List<EmitterFix> findTopThreats(int limit);

    default List<EmitterLob> findLobs(
        Integer lastMinutes,
        Boolean unassociatedOnly,
        String sensorNodeId
    ) {
        return findLobs(lastMinutes, unassociatedOnly, sensorNodeId, null, null, null);
    }

    List<EmitterLob> findLobs(
        Integer lastMinutes,
        Boolean unassociatedOnly,
        String sensorNodeId,
        String band,
        Double minFrequencyMhz,
        Double maxFrequencyMhz
    );

    /**
     * Güvenli Sayfalama: Ham LOB hatlarını sayfalı olarak döner.
     */
    default PagedResult<EmitterLob> findLobsPaged(
        int page,
        int pageSize,
        Boolean unassociatedOnly,
        String sensorNodeId,
        String band
    ) {
        String status = Boolean.TRUE.equals(unassociatedOnly) ? "UNASSOCIATED" : "ALL";
        return findLobsPaged(page, pageSize, status, sensorNodeId, band);
    }

    PagedResult<EmitterLob> findLobsPaged(
        int page,
        int pageSize,
        String associationStatus,
        String sensorNodeId,
        String band
    );

    default Map<String, Object> countLobs(
        Integer lastMinutes,
        Boolean unassociatedOnly,
        String sensorNodeId,
        String band,
        Double minFrequencyMhz,
        Double maxFrequencyMhz
    ) {
        String status = Boolean.TRUE.equals(unassociatedOnly) ? "UNASSOCIATED" : "ALL";
        return countLobs(lastMinutes, status, sensorNodeId, band, minFrequencyMhz, maxFrequencyMhz);
    }

    Map<String, Object> countLobs(
        Integer lastMinutes,
        String associationStatus,
        String sensorNodeId,
        String band,
        Double minFrequencyMhz,
        Double maxFrequencyMhz
    );

    List<EmitterLob> findContributingLobsForFix(String fixId);

    Optional<EmitterFix> findFixById(String fixId);

    Optional<EmitterLob> findLobById(String lobId);

    List<EmitterFix> findAllFixes();

    List<EmitterLob> findAllLobs();

    Map<String, Object> getOperationalStatistics();
}
