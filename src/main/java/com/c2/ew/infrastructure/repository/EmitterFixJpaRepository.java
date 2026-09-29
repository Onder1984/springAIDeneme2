package com.c2.ew.infrastructure.repository;

import com.c2.ew.domain.model.EmitterActivity;
import com.c2.ew.infrastructure.entity.EmitterFixEntity;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface EmitterFixJpaRepository extends JpaRepository<EmitterFixEntity, String>, JpaSpecificationExecutor<EmitterFixEntity> {

    Page<EmitterFixEntity> findByBandIgnoreCase(String band, Pageable pageable);

    Page<EmitterFixEntity> findByStatus(EmitterActivity status, Pageable pageable);

    Page<EmitterFixEntity> findByThreatLevelGreaterThanEqual(int minThreatLevel, Pageable pageable);

    long countByStatus(EmitterActivity status);

    long countByBandIgnoreCase(String band);

    @Query("SELECT f.band, COUNT(f) FROM EmitterFixEntity f GROUP BY f.band ORDER BY COUNT(f) DESC")
    List<Object[]> countGroupByBand();

    @Query("SELECT f.status, COUNT(f) FROM EmitterFixEntity f GROUP BY f.status")
    List<Object[]> countGroupByStatus();

    @Query("SELECT f.threatLevel, COUNT(f) FROM EmitterFixEntity f GROUP BY f.threatLevel ORDER BY f.threatLevel DESC")
    List<Object[]> countGroupByThreatLevel();

    @Query("SELECT f.platformType, COUNT(f) FROM EmitterFixEntity f GROUP BY f.platformType ORDER BY COUNT(f) DESC")
    List<Object[]> countGroupByPlatformType();

    @Query("SELECT f.radarType, COUNT(f) FROM EmitterFixEntity f GROUP BY f.radarType ORDER BY COUNT(f) DESC")
    List<Object[]> countGroupByRadarType();

    @Query("SELECT AVG(f.frequencyMhz) FROM EmitterFixEntity f WHERE UPPER(f.band) = UPPER(:band)")
    Double findAvgFrequencyByBand(@Param("band") String band);

    @Query("SELECT f FROM EmitterFixEntity f ORDER BY f.threatLevel DESC, f.lastSeen DESC")
    List<EmitterFixEntity> findTopThreats(Pageable pageable);
}
