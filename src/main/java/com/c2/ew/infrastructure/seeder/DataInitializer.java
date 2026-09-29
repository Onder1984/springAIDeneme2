package com.c2.ew.infrastructure.seeder;

import com.c2.ew.domain.model.EmitterActivity;
import com.c2.ew.infrastructure.entity.EmitterFixEntity;
import com.c2.ew.infrastructure.entity.EmitterLobEntity;
import com.c2.ew.infrastructure.repository.EmitterFixJpaRepository;
import com.c2.ew.infrastructure.repository.EmitterLobJpaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL Kalıcı Veri Başlatıcı (Data Seeder)
 * Veritabanında veri varsa korur (asla silmez), boşsa 150 Fix ve 5.000 LOB üretir.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final EmitterFixJpaRepository fixJpaRepository;
    private final EmitterLobJpaRepository lobJpaRepository;

    public DataInitializer(EmitterFixJpaRepository fixJpaRepository, EmitterLobJpaRepository lobJpaRepository) {
        this.fixJpaRepository = fixJpaRepository;
        this.lobJpaRepository = lobJpaRepository;
    }

    @Override
    public void run(String... args) {
        long existingFixes = fixJpaRepository.count();
        long existingLobs = lobJpaRepository.count();

        if (existingFixes > 0 || existingLobs > 0) {
            log.info("🛡️ [VERİ KALICILIĞI AKTİF] PostgreSQL'de mevcut {} adet Fix ve {} adet LOB tespit edildi. Mevcut veriler korunuyor.",
                existingFixes, existingLobs);
            backfillRadarTypesIfMissing();
            return;
        }

        log.info("⚡ [İLK KURULUM] PostgreSQL veritabanı boş. 150 adet Taktik Fix ve 5.000 adet LOB üretiliyor...");

        Random rnd = new Random(42);
        Instant now = Instant.now();

        // 1. Taktik Kestirimler (EmitterFix) Üretimi (150 Adet)
        List<EmitterFixEntity> fixes = new ArrayList<>();
        String[] bands = {"X", "S", "C", "Ku", "L"};
        String[] modulations = {"PULSE_DOPPLER", "STAGGERED_PRI", "FREQUENCY_AGILE", "CHIRP_FM", "CW"};

        // Kritik Taktik Bölgeler (Merkez koordinatlar)
        double[][] operationalSectors = {
            {39.9334, 32.8597}, // Ankara
            {39.7767, 30.5206}, // Eskişehir
            {37.8714, 32.4846}, // Konya
            {38.4237, 27.1428}, // İzmir / Ege
            {41.0082, 28.9784}, // Marmara / Boğazlar
            {36.8841, 30.7056}, // Antalya / Akdeniz
            {41.2797, 36.3361}, // Karadeniz
            {37.0662, 37.3833}  // Güneydoğu
        };

        for (int i = 1; i <= 150; i++) {
            String fixId = String.format("FIX-%04d", i);
            int sectorIdx = rnd.nextInt(operationalSectors.length);
            double baseLat = operationalSectors[sectorIdx][0];
            double baseLon = operationalSectors[sectorIdx][1];

            // Sektör etrafında 15-80 km saçılım
            double lat = baseLat + (rnd.nextDouble() - 0.5) * 1.2;
            double lon = baseLon + (rnd.nextDouble() - 0.5) * 1.5;

            String band = bands[rnd.nextInt(bands.length)];
            double freq;
            int threatLevel;
            switch (band) {
                case "Ku" -> { freq = 12000.0 + rnd.nextDouble() * 4000.0; threatLevel = 5; } // Kritik Atış Kontrol
                case "X" -> { freq = 8500.0 + rnd.nextDouble() * 2500.0; threatLevel = 4; }  // Hedef Takip / Atış Kontrol
                case "C" -> { freq = 4000.0 + rnd.nextDouble() * 3000.0; threatLevel = 3; }  // Orta Menzil Arama
                case "S" -> { freq = 2000.0 + rnd.nextDouble() * 1800.0; threatLevel = 2; }  // Erken İhbar
                default -> { freq = 1000.0 + rnd.nextDouble() * 900.0; threatLevel = 1; }    // Gözetleme
            }

            EmitterActivity status = (rnd.nextDouble() < 0.65) ? EmitterActivity.ACTIVE :
                                     (rnd.nextDouble() < 0.85 ? EmitterActivity.INTERMITTENT : EmitterActivity.SILENT);

            int minutesAgo = rnd.nextInt(180);
            Instant lastSeen = now.minus(minutesAgo, ChronoUnit.MINUTES);
            Instant firstSeen = lastSeen.minus(rnd.nextInt(120) + 30, ChronoUnit.MINUTES);

            double semiMajor = 800.0 + rnd.nextDouble() * 4500.0; // 800m - 5300m
            double semiMinor = semiMajor * (0.3 + rnd.nextDouble() * 0.5);
            double orientation = rnd.nextDouble() * 360.0;
            int confidence = 65 + rnd.nextInt(35);
            int sourceLobCount = 10 + rnd.nextInt(60);

            String radarType = determineRadarType(band, threatLevel, rnd);
            String platformType = determinePlatformType(band, rnd);

            EmitterFixEntity fixEntity = new EmitterFixEntity(
                fixId, firstSeen, lastSeen, status, band,
                Math.round(freq * 10.0) / 10.0,
                150.0 + rnd.nextDouble() * 800.0, // PRI us
                0.8 + rnd.nextDouble() * 8.0,     // PW us
                modulations[rnd.nextInt(modulations.length)],
                lat, lon, 450.0 + rnd.nextDouble() * 800.0,
                semiMajor, semiMinor, orientation, confidence, sourceLobCount, threatLevel,
                radarType, platformType
            );
            fixes.add(fixEntity);
        }
        fixJpaRepository.saveAll(fixes);
        log.info("✅ 150 adet EmitterFix başarıyla PostgreSQL'e kaydedildi.");

        // 2. Ham Yön Bulma Hatları (EmitterLob) Üretimi (5.000 Adet)
        List<EmitterLobEntity> lobs = new ArrayList<>();
        String[] sensors = {"DF-ALPHA", "DF-BRAVO", "DF-CHARLIE", "DF-DELTA", "UAV-POD-1", "UAV-POD-2"};
        double[][] sensorCoords = {
            {39.95, 32.88}, // Alpha
            {39.75, 30.50}, // Bravo
            {37.90, 32.50}, // Charlie
            {38.45, 27.15}, // Delta
            {40.50, 31.50}, // UAV-1
            {38.50, 34.00}  // UAV-2
        };

        for (int i = 1; i <= 5000; i++) {
            String lobId = String.format("LOB-%06d", i);
            int sIdx = rnd.nextInt(sensors.length);
            String sensorId = sensors[sIdx];
            double sLat = sensorCoords[sIdx][0];
            double sLon = sensorCoords[sIdx][1];

            // İlişkili fix (bazıları ilişkisiz / yetim LOB)
            String associatedFixId = null;
            if (rnd.nextDouble() < 0.70) {
                associatedFixId = fixes.get(rnd.nextInt(fixes.size())).getFixId();
            }

            String band = bands[rnd.nextInt(bands.length)];
            double freq = 1000.0 + rnd.nextDouble() * 14000.0;
            double bearing = rnd.nextDouble() * 360.0;
            double angularAcc = 0.5 + rnd.nextDouble() * 2.5;

            Instant lobTime = now.minus(rnd.nextInt(240), ChronoUnit.MINUTES);

            EmitterLobEntity lob = new EmitterLobEntity(
                lobId, sensorId, lobTime, sLat, sLon,
                Math.round(bearing * 10.0) / 10.0,
                Math.round(angularAcc * 10.0) / 10.0,
                Math.round(freq * 10.0) / 10.0,
                band,
                modulations[rnd.nextInt(modulations.length)],
                200.0 + rnd.nextDouble() * 600.0,
                1.0 + rnd.nextDouble() * 5.0,
                associatedFixId
            );
            lobs.add(lob);
        }

        // 1000'lik partiler halinde kaydet
        int batchSize = 1000;
        for (int i = 0; i < lobs.size(); i += batchSize) {
            int end = Math.min(i + batchSize, lobs.size());
            lobJpaRepository.saveAll(lobs.subList(i, end));
        }
        log.info("✅ 5.000 adet EmitterLob başarıyla PostgreSQL'e kaydedildi. Veriler Docker Volume içinde kalıcıdır!");
    }

    private void backfillRadarTypesIfMissing() {
        List<EmitterFixEntity> fixes = fixJpaRepository.findAll();
        boolean updated = false;
        Random rnd = new Random(101);
        for (EmitterFixEntity fix : fixes) {
            if (fix.getRadarType() == null || fix.getRadarType().isBlank() || "Bilinmeyen Radar".equalsIgnoreCase(fix.getRadarType())) {
                fix.setRadarType(determineRadarType(fix.getBand(), fix.getThreatLevel(), rnd));
                fix.setPlatformType(determinePlatformType(fix.getBand(), rnd));
                updated = true;
            }
        }
        if (updated) {
            fixJpaRepository.saveAll(fixes);
            log.info("🎯 [GERİYE DÖNÜK ZENGİNLEŞTİRME] Mevcut {} adet Fix kaydına radarType ve platformType başarıyla atandı.", fixes.size());
        }
    }

    private String determineRadarType(String band, int threatLevel, Random rnd) {
        if ("Ku".equalsIgnoreCase(band)) {
            String[] kuRadars = {
                "92N6E Tomb Stone (S-400)", "30N6E Flap Lid (S-300)", "AN/MPQ-65 (Patriot PAC-3)",
                "Skyguard Atış Kontrol", "96L6E Çift Kutuplu Radar"
            };
            return kuRadars[rnd.nextInt(kuRadars.length)];
        } else if ("X".equalsIgnoreCase(band)) {
            String[] xRadars = {
                "AN/APG-68 (F-16 Uçak Radarı)", "Flycatcher Atış Kontrol", "Thales SMART-S Mk2",
                "P-40 Long Track", "AN/SPY-1D (Aegis Füze Takip)"
            };
            return xRadars[rnd.nextInt(xRadars.length)];
        } else if ("C".equalsIgnoreCase(band)) {
            String[] cRadars = {
                "AN/MPQ-64 Sentinel", "Giraffe AMB (Hava Savunma)", "P-37 Bar Lock",
                "Kalkan Hava Savunma Radarı"
            };
            return cRadars[rnd.nextInt(cRadars.length)];
        } else if ("S".equalsIgnoreCase(band)) {
            String[] sRadars = {
                "P-18 Spoon Rest (Erken İhbar)", "TRS-2215 Hava Radarı", "SPS-49 (Hava Gözetleme)",
                "Airborne Early Warning (AEW)"
            };
            return sRadars[rnd.nextInt(sRadars.length)];
        } else {
            String[] lRadars = {
                "P-14 Tall King (Erken İhbar)", "AN/TPS-77 Uzun Menzil", "AESA Gözetleme Radarı"
            };
            return lRadars[rnd.nextInt(lRadars.length)];
        }
    }

    private String determinePlatformType(String band, Random rnd) {
        if ("X".equalsIgnoreCase(band)) {
            String[] xPlatforms = {"AIRBORNE", "NAVAL", "LAND_MOBILE"};
            return xPlatforms[rnd.nextInt(xPlatforms.length)];
        } else if ("S".equalsIgnoreCase(band) || "C".equalsIgnoreCase(band)) {
            String[] sPlatforms = {"LAND_MOBILE", "FIXED_SITE", "AIRBORNE", "NAVAL"};
            return sPlatforms[rnd.nextInt(sPlatforms.length)];
        } else if ("Ku".equalsIgnoreCase(band)) {
            String[] kuPlatforms = {"LAND_MOBILE", "FIXED_SITE"};
            return kuPlatforms[rnd.nextInt(kuPlatforms.length)];
        } else {
            String[] lPlatforms = {"FIXED_SITE", "LAND_MOBILE"};
            return lPlatforms[rnd.nextInt(lPlatforms.length)];
        }
    }
}
