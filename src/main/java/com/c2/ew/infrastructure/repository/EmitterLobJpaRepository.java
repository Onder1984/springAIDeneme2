package com.c2.ew.infrastructure.repository;

import com.c2.ew.infrastructure.entity.EmitterLobEntity;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface EmitterLobJpaRepository extends JpaRepository<EmitterLobEntity, String>, JpaSpecificationExecutor<EmitterLobEntity> {

    Page<EmitterLobEntity> findBySensorNodeId(String sensorNodeId, Pageable pageable);

    Page<EmitterLobEntity> findByBandIgnoreCase(String band, Pageable pageable);

    long countByAssociatedFixIdIsNull();

    @Query("SELECT l.band, COUNT(l) FROM EmitterLobEntity l GROUP BY l.band ORDER BY COUNT(l) DESC")
    List<Object[]> countGroupByBand();

    @Query("SELECT l.sensorNodeId, COUNT(l) FROM EmitterLobEntity l GROUP BY l.sensorNodeId ORDER BY COUNT(l) DESC")
    List<Object[]> countGroupBySensorNode();
}
