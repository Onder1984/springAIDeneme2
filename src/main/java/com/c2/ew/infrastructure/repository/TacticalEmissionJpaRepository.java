package com.c2.ew.infrastructure.repository;

import com.c2.ew.domain.enums.*;
import com.c2.ew.infrastructure.entity.TacticalEmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface TacticalEmissionJpaRepository extends JpaRepository<TacticalEmissionEntity, String>, JpaSpecificationExecutor<TacticalEmissionEntity> {

    long countByTacizTrue();

    long countByEtUygulamaDurumu(EtUygulamaDurumu etUygulamaDurumu);

    long countByTeshisKimlik(TeshisKimlik teshisKimlik);

    long countByVeriKaynagi(VeriKaynagi veriKaynagi);

    @Query("SELECT e.teshisKimlik, COUNT(e) FROM TacticalEmissionEntity e GROUP BY e.teshisKimlik")
    List<Object[]> getTeshisDistribution();

    @Query("SELECT e.veriKaynagi, COUNT(e) FROM TacticalEmissionEntity e GROUP BY e.veriKaynagi")
    List<Object[]> getVeriKaynagiDistribution();

    @Query("SELECT e.platformOrtami, COUNT(e) FROM TacticalEmissionEntity e GROUP BY e.platformOrtami")
    List<Object[]> getPlatformOrtamiDistribution();

    @Query("SELECT e.etUygulamaDurumu, COUNT(e) FROM TacticalEmissionEntity e GROUP BY e.etUygulamaDurumu")
    List<Object[]> getEtUygulamaDistribution();
}
