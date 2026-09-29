package com.c2.ew;

import com.c2.ew.domain.model.EmitterFix;
import com.c2.ew.domain.model.EmitterLob;
import com.c2.ew.domain.model.PagedResult;
import com.c2.ew.infrastructure.repository.EmitterFixJpaRepository;
import com.c2.ew.infrastructure.repository.EmitterLobJpaRepository;
import com.c2.ew.infrastructure.repository.TacticalEmissionRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class TacticalPostgreSqlIntegrationTest {

    @Autowired
    private EmitterFixJpaRepository fixJpaRepository;

    @Autowired
    private EmitterLobJpaRepository lobJpaRepository;

    @Autowired
    private TacticalEmissionRepository tacticalRepository;

    @Test
    @DisplayName("PostgreSQL veritabanında 150 Fix ve 5000 LOB kalıcı olarak yüklenmiş olmalıdır")
    void testDataSeedingAndPersistence() {
        long fixCount = fixJpaRepository.count();
        long lobCount = lobJpaRepository.count();

        assertTrue(fixCount >= 150, "En az 150 adet Fix olmalıdır, bulunan: " + fixCount);
        assertTrue(lobCount >= 5000, "En az 5000 adet LOB olmalıdır, bulunan: " + lobCount);
    }

    @Test
    @DisplayName("SQL Düzeyinde Makro İstatistikler (Group-By ve Count) anında hesaplanmalıdır")
    void testOperationalStatistics() {
        Map<String, Object> stats = tacticalRepository.getOperationalStatistics();

        assertNotNull(stats);
        assertTrue((Long) stats.get("totalFixes") >= 150);
        assertTrue((Long) stats.get("totalLobs") >= 5000);
        assertTrue((Long) stats.get("activeFixes") > 0);

        Map<?, ?> fixesByBand = (Map<?, ?>) stats.get("fixesByBand");
        assertNotNull(fixesByBand);
        assertFalse(fixesByBand.isEmpty(), "Bant dağılımı boş olmamalıdır");
    }

    @Test
    @DisplayName("PagedResult ile güvenli sayfalama çalışmalı ve tavan sınırını korumalıdır")
    void testPagedResultProtection() {
        PagedResult<EmitterFix> page1 = tacticalRepository.findFixesPaged(0, 15, null, null, null);

        assertNotNull(page1);
        assertEquals(15, page1.items().size(), "Sayfadaki eleman adedi 15 olmalıdır");
        assertEquals(0, page1.page());
        assertTrue(page1.totalCount() >= 150);
        assertTrue(page1.hasMore(), "Daha fazla sayfa olmalıdır");
        assertNotNull(page1.note());

        PagedResult<EmitterLob> lobsPage = tacticalRepository.findLobsPaged(0, 20, false, null, null);
        assertNotNull(lobsPage);
        assertEquals(20, lobsPage.items().size());
        assertTrue(lobsPage.totalCount() >= 5000);

        PagedResult<EmitterLob> orphanLobs = tacticalRepository.findLobsPaged(0, 20, true, null, null);
        assertNotNull(orphanLobs);
        assertTrue(orphanLobs.totalCount() > 0, "Yetim LOB bulunmalıdır");
        assertFalse(orphanLobs.items().isEmpty(), "Yetim LOB listesi boş olmamalıdır");

        PagedResult<EmitterLob> associatedKuLobs = tacticalRepository.findLobsPaged(0, 20, "ASSOCIATED", null, "Ku");
        assertNotNull(associatedKuLobs);
        assertTrue(associatedKuLobs.totalCount() > 0, "İlişkilendirilmiş Ku-bant LOB bulunmalıdır");
        assertFalse(associatedKuLobs.items().isEmpty());
        for (EmitterLob lob : associatedKuLobs.items()) {
            assertNotNull(lob.lobId());
            assertEquals("Ku", lob.rfSignature().band());
        }
    }

    @Test
    @DisplayName("Top-K Öncelikli Tehdit sorgusu en kritik hedefleri dönmelidir")
    void testTopThreats() {
        List<EmitterFix> topThreats = tacticalRepository.findTopThreats(10);

        assertNotNull(topThreats);
        assertEquals(10, topThreats.size());
        assertNotNull(topThreats.get(0).radarType(), "Radar tipi dolu olmalıdır");
        assertNotNull(topThreats.get(0).platformType(), "Platform türü dolu olmalıdır");
        // En yüksek tehdit seviyeleri önce gelmeli
        assertTrue(topThreats.get(0).rfSignature().band().equals("Ku") || topThreats.get(0).rfSignature().band().equals("X"),
            "En üstteki tehdit kritik bantta olmalıdır");

        // Zaman-Frekans noktaları testi
        List<Map<String, Object>> timeFreqPoints = tacticalRepository.getTimeFrequencyPoints(24, null, null, null);
        assertNotNull(timeFreqPoints);
        assertFalse(timeFreqPoints.isEmpty(), "Zaman-Frekans noktaları boş olmamalıdır");
        assertTrue(timeFreqPoints.get(0).containsKey("frequencyMhz"));
        assertTrue(timeFreqPoints.get(0).containsKey("radarType"));
    }
}
