package com.c2.ew.infrastructure.seeder;

import com.c2.ew.domain.enums.*;
import com.c2.ew.domain.model.*;
import com.c2.ew.infrastructure.entity.TacticalComintEntity;
import com.c2.ew.infrastructure.entity.TacticalEmissionEntity;
import com.c2.ew.infrastructure.repository.TacticalComintJpaRepository;
import com.c2.ew.infrastructure.repository.TacticalEmissionJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final TacticalEmissionJpaRepository emissionRepo;
    private final TacticalComintJpaRepository comintRepo;
    private final ObjectMapper objectMapper;

    public DataInitializer(
        TacticalEmissionJpaRepository emissionRepo,
        TacticalComintJpaRepository comintRepo,
        ObjectMapper objectMapper
    ) {
        this.emissionRepo = emissionRepo;
        this.comintRepo = comintRepo;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        seedRadarEmissions();
        seedComintEmissions();
    }

    private void seedRadarEmissions() throws Exception {
        long count = emissionRepo.count();
        if (count > 0) {
            log.info("🛡️ [VERİ KALICILIĞI AKTİF] PostgreSQL'de mevcut {} adet Taktik Radar Emisyon kaydı tespit edildi.", count);
            return;
        }

        log.info("🚀 [DATA SEEDER] Taktik Radar Emisyon veritabanı boş. 37 alanlı 160 adet gerçekçi radar emisyonu üretiliyor...");
        List<TacticalEmissionEntity> emissions = new ArrayList<>();
        Random rnd = new Random(42);

        String[] radarModels = {
            "Pantsir-S1 1RS2-1E", "S-400 92N6E Grave Stone", "Patriot AN/MPQ-53",
            "Nebo-M VHF/L", "KORAL-EW Jammer", "AN/APG-77 AESA", "AN/APG-81 AESA",
            "Sea Sparrow Mk-95", "Aegis SPY-1D", "Buk-M2 9S36", "Tor-M2 9A331",
            "S-300PMU2 Flap Lid", "Barış Kartalı MESA", "Aselsan EIRS", "Kalkan Hava Savunma Radarı"
        };

        String[] locations = {
            "Ege Denizi / Midilli Doğusu", "Ege Denizi / Sakız Batısı", "Ege Denizi / Rodos Açıkları",
            "Doğu Akdeniz / Kıbrıs Kuzeyi", "Akdeniz / Girit Açıkları", "Kıbrıs / Baf Açıkları",
            "Karadeniz / Kırım Güneyi", "Karadeniz / Sivastopol Önleri",
            "Güneydoğu / Şırnak Hudut Hattı", "Suriye Hududu / Tel Rıfat Sektörü",
            "Marmara / Bandırma Hava Sahası", "İç Anadolu / Konya Atış Sahası"
        };

        String[] sensorUnits = {
            "KORAL-1 EH Sistemi", "KORAL-2 EH Sistemi", "DF-KARA-BATARYA-03",
            "HAVA-SOJ-01", "İHA-DF-Pod-1", "İHA-DF-Pod-2", "RADNET-KARA-04",
            "BARBAROS-F244-ESM", "TCG-BURAK-RADNET"
        };

        VeriKaynagi[] veriKaynaklari = VeriKaynagi.values();
        TeshisKimlik[] teshisler = TeshisKimlik.values();
        PlatformOrtami[] ortamlar = PlatformOrtami.values();
        PlatformTipi[] platformTipleri = PlatformTipi.values();
        Polarizasyon[] polarizasyonlar = Polarizasyon.values();
        Modulasyon[] modulasyonlar = Modulasyon.values();
        EtUygulamaDurumu[] etDurumlari = EtUygulamaDurumu.values();
        HedefKaynagi[] hedefKaynaklari = HedefKaynagi.values();

        for (int i = 1; i <= 160; i++) {
            TacticalEmissionEntity e = new TacticalEmissionEntity();
            e.setId(String.format("EMI-%04d", 1000 + i));
            e.setVeriKaynagi(veriKaynaklari[rnd.nextInt(veriKaynaklari.length)]);
            e.setKuvvetSiraNo(String.format("KUV-%04d", 5000 + i));
            e.setUstBirlikRef("2. Taktik EH Tabur Komutanlığı");
            e.setEhUnsuru(sensorUnits[rnd.nextInt(sensorUnits.length)]);

            double ehLat = 36.5 + (rnd.nextDouble() * 5.0);
            double ehLon = 26.5 + (rnd.nextDouble() * 14.0);
            e.setEhUnsuruEnlem(ehLat);
            e.setEhUnsuruBoylam(ehLon);
            e.setEhUnsuruIrtifa(100.0 + rnd.nextInt(9000));

            double targetLat = ehLat + (rnd.nextDouble() - 0.5) * 1.5;
            double targetLon = ehLon + (rnd.nextDouble() - 0.5) * 1.5;
            e.setYayinEnlem(targetLat);
            e.setYayinBoylam(targetLon);

            double dLat = targetLat - ehLat;
            double dLon = targetLon - ehLon;
            double bearing = (Math.toDegrees(Math.atan2(dLon, dLat)) + 360.0) % 360.0;
            e.setYon(bearing);

            e.setYayinSemiMajorMeters(2000.0 + rnd.nextInt(7000));
            e.setYayinSemiMinorMeters(800.0 + rnd.nextInt(2500));
            e.setYayinOrientationDegrees((double) rnd.nextInt(180));

            e.setHedefYerBilgisi(locations[rnd.nextInt(locations.length)]);
            e.setHedefMevziBilgisi("Mevzi-" + (rnd.nextInt(20) + 1));

            TeshisKimlik teshis = teshisler[rnd.nextInt(teshisler.length)];
            e.setTeshisKimlik(teshis);
            e.setElintNotasyonu(String.format("ELN-%02X%02X", rnd.nextInt(255), rnd.nextInt(255)));
            e.setHedefKaynagi(hedefKaynaklari[rnd.nextInt(hedefKaynaklari.length)]);
            e.setPolarizasyon(polarizasyonlar[rnd.nextInt(polarizasyonlar.length)]);
            e.setSpotNo(String.format("SP%03d", i % 100));
            e.setPulseCw(rnd.nextBoolean() ? PulseCw.PULSE : PulseCw.CW);

            String model = radarModels[rnd.nextInt(radarModels.length)];
            e.setRadarAdi(model);
            e.setRadarGorevi(model.contains("Pantsir") ? "Atış Kontrol" : (model.contains("Nebo") ? "Erken İhbar" : "Takip/Arama"));
            e.setPlatformOrtami(ortamlar[rnd.nextInt(ortamlar.length)]);
            e.setPlatformTipi(platformTipleri[rnd.nextInt(platformTipleri.length)]);
            e.setGenlikDbm(-85.0 + rnd.nextDouble() * 50.0);
            e.setModulasyon(modulasyonlar[rnd.nextInt(modulasyonlar.length)]);
            e.setEtUygulamaDurumu(etDurumlari[rnd.nextInt(etDurumlari.length)]);

            Instant now = Instant.now().minus(rnd.nextInt(72), ChronoUnit.HOURS);
            e.setSonTespitZamani(now);
            e.setIlkTespitZamani(now.minus(rnd.nextInt(3600) + 300, ChronoUnit.SECONDS));
            e.setSureSn(120.0 + rnd.nextInt(2400));

            e.setHss(model.contains("S-400") ? "S-400 Triumf" : (model.contains("Patriot") ? "Patriot PAC-3" : (model.contains("Hisar") ? "Hisar-O" : "Stand-alone")));
            boolean taciz = (teshis == TeshisKimlik.DUSMAN || teshis == TeshisKimlik.MUHTEMEL_DUSMAN) && rnd.nextBoolean();
            e.setTaciz(taciz);
            e.setRadarKesitAlani(1.5 + rnd.nextDouble() * 8.0);
            e.setRadarIzNumarasi(String.format("TRK-%04d", 3000 + i));
            e.setUcakKuyrukNumarasi(e.getPlatformOrtami() == PlatformOrtami.HAVA ? String.format("TU-%03d", rnd.nextInt(900) + 100) : null);

            double baseFreq;
            if (model.contains("Nebo-M")) baseFreq = 150.0 + rnd.nextInt(200);
            else if (model.contains("Pantsir") || model.contains("92N6E") || model.contains("MPQ-53") || model.contains("AN/APG")) baseFreq = 8500.0 + rnd.nextInt(2500);
            else if (model.contains("SPY-1D")) baseFreq = 3100.0 + rnd.nextInt(400);
            else baseFreq = 5000.0 + rnd.nextInt(4000);

            List<FrequencyEntry> fList = new ArrayList<>();
            fList.add(new FrequencyEntry(rnd.nextBoolean() ? "ATLAMALI_HOPPING" : "SABIT", baseFreq, baseFreq + (rnd.nextInt(150) + 20)));
            if (rnd.nextBoolean()) {
                fList.add(new FrequencyEntry("DEGISKEN_JITTER", baseFreq + 200, baseFreq + 400));
            }
            e.setMinFrekansMhz(fList.stream().mapToDouble(FrequencyEntry::minFrekansMhz).min().orElse(baseFreq));
            e.setMaxFrekansMhz(fList.stream().mapToDouble(FrequencyEntry::maxFrekansMhz).max().orElse(baseFreq + 100));
            e.setFrekansListesiJson(objectMapper.writeValueAsString(fList));

            double basePri = 400.0 + rnd.nextInt(1600);
            List<PriEntry> priList = new ArrayList<>();
            priList.add(new PriEntry(rnd.nextBoolean() ? "KADEMELI" : "SABIT", basePri, basePri + rnd.nextInt(200)));
            e.setMinPriMicroSec(priList.stream().mapToDouble(PriEntry::minPriMicroSec).min().orElse(basePri));
            e.setMaxPriMicroSec(priList.stream().mapToDouble(PriEntry::maxPriMicroSec).max().orElse(basePri + 200));
            e.setPriListesiJson(objectMapper.writeValueAsString(priList));

            double basePw = 0.5 + rnd.nextDouble() * 5.0;
            List<PwEntry> pwList = new ArrayList<>();
            pwList.add(new PwEntry(rnd.nextBoolean() ? "MODULASYONLU" : "SABIT", basePw, basePw + rnd.nextDouble() * 2.0));
            e.setMinPwMicroSec(pwList.stream().mapToDouble(PwEntry::minPwMicroSec).min().orElse(basePw));
            e.setMaxPwMicroSec(pwList.stream().mapToDouble(PwEntry::maxPwMicroSec).max().orElse(basePw + 1.0));
            e.setPwListesiJson(objectMapper.writeValueAsString(pwList));

            double baseAtp = (1.5 + rnd.nextDouble() * 6.0) * 1_000_000.0;
            List<AtpEntry> atpList = new ArrayList<>();
            atpList.add(new AtpEntry(rnd.nextBoolean() ? "ELEKTRONIK_TARAMA" : "DAIRESEL", baseAtp));
            e.setMinAtpMicroSec(baseAtp);
            e.setMaxAtpMicroSec(baseAtp + 500_000.0);
            e.setAtpListesiJson(objectMapper.writeValueAsString(atpList));

            if (taciz) {
                e.setOperatorNotu("Düşman unsuru hudut hattını taciz etti. Elektronik taarruz tedbirleri devreye alındı.");
                e.setOnaylayanKullanici("Bnb. Serdar Yılmaz");
                e.setOnayNotu("Taktik EH Müdahalesi Onaylandı.");
            } else {
                e.setOperatorNotu("Rutin taktik radar izleme ve kestirim kaydı.");
                e.setOnaylayanKullanici("Yzb. Can Demir");
                e.setOnayNotu("Kayıt incelendi, kütüphane imzası ile eşleşti.");
            }
            emissions.add(e);
        }

        emissionRepo.saveAll(emissions);
        log.info("✅ [DATA SEEDER] 160 adet Taktik Radar Emisyonu başarıyla kaydedildi.");
    }

    private void seedComintEmissions() throws Exception {
        long count = comintRepo.count();
        if (count > 0) {
            log.info("🛡️ [VERİ KALICILIĞI AKTİF] PostgreSQL'de mevcut {} adet Taktik Muhabere (COMINT) kaydı tespit edildi.", count);
            return;
        }

        log.info("🚀 [DATA SEEDER] Taktik Muhabere veritabanı boş. 30 alanlı 120 adet gerçekçi COMINT telsiz yayını üretiliyor...");
        List<TacticalComintEntity> comints = new ArrayList<>();
        Random rnd = new Random(101);

        String[][] callsignPairs = {
            {"VOLGA-01", "VOLGA-04", "Rusça", "DUSMAN", "VHF", "DMR_MOBIL", "KRIPTO"},
            {"VOLGA-04", "VOLGA-HQ", "Rusça", "DUSMAN", "HF", "BELIRSIZ", "SES"},
            {"ZARYA-3", "ZARYA-BASE", "Rusça", "DUSMAN", "VHF", "DMR_BAZ", "KRIPTO"},
            {"OMAR-11", "OMAR-ROLE", "Arapça", "DUSMAN", "VHF", "DMR_MOBIL", "SES"},
            {"OMAR-ROLE", "KUZEY-HQ", "Arapça", "DUSMAN", "VHF", "DMR_BAZ", "SES"},
            {"TARIQ-02", "OMAR-11", "Arapça", "DUSMAN", "VHF", "D_STAR", "DARBE_PATLAMA"},
            {"SAHRA-7", "KUZEY-HQ", "Arapça", "MUHTEMEL_DUSMAN", "HF", "BELIRSIZ", "SES"},
            {"JAM-ALPHA-01", "SECTOR-JAM-NET", "Tanımsız", "DUSMAN", "VHF", "TANIMSIZ", "KARISTIRMA_GAYDA"},
            {"ELECTRON-NOISE", "TARGET-NET", "Tanımsız", "DUSMAN", "VHF", "TANIMSIZ", "KARISTIRMA_GURULTU"},
            {"AEGEAN-PATROL-1", "RHODES-BASE", "Yunanca", "DUSMAN", "VHF", "DMR_MOBIL", "SES"},
            {"AEGEAN-PATROL-2", "ATHENS-RELAY", "Yunanca", "MUHTEMEL_DUSMAN", "HF", "BELIRSIZ", "VERI"},
            {"HERMES-4", "ATHENS-RELAY", "Yunanca", "DUSMAN", "VHF", "DMR_BAZ", "KRIPTO"},
            {"KARTAL-1", "KARTAL-KOMUTA", "Türkçe", "DOST", "VHF", "DMR_MOBIL", "KRIPTO"},
            {"PARS-02", "HILAL-BAZ", "Türkçe", "DOST", "VHF", "DMR_BAZ", "SES"},
            {"GOKTURK-3", "ANKARA-MERKEZ", "Türkçe", "DOST", "HF", "BELIRSIZ", "VERI"},
            {"NATO-TASK-09", "MED-AIR-CONTROL", "İngilizce", "DOST", "VHF", "DMR_MOBIL", "SES"}
        };

        String[] comintSensors = {
            "MİLKAR-3A3 Taktik Telsiz EH", "MİLKAR-4A2 Muhabere Karıştırma",
            "REDET-II Muhabere DF/Dinleme", "İHA-DF-Pod-1", "HAVA-SOJ-01 (CESM)",
            "TCG-PREVEZE-CESM", "RADNET-MUHABERE-02"
        };

        String[] comLocations = {
            "Ege Denizi / Sakız Batısı", "Ege Denizi / Midilli Doğusu", "Rodos Açıkları",
            "Doğu Akdeniz / Kıbrıs Kuzeyi", "Kıbrıs / Baf Açıkları", "Karadeniz / Kırım Güneyi",
            "Suriye Hududu / Tel Rıfat Sektörü", "Güneydoğu / Şırnak Hudut Hattı",
            "Irak Hududu / Zap Sektörü", "Akdeniz / Girit Açıkları"
        };

        VeriKaynagi[] veriKaynaklari = VeriKaynagi.values();
        MuhabereModulasyon[] modList = {MuhabereModulasyon.FM, MuhabereModulasyon.AM, MuhabereModulasyon.LSB, MuhabereModulasyon.USB, MuhabereModulasyon.FSK8, MuhabereModulasyon.CPSK2};
        CalismaSekli[] calismaList = CalismaSekli.values();
        HedefKaynagi[] kaynakList = HedefKaynagi.values();

        for (int i = 1; i <= 120; i++) {
            TacticalComintEntity c = new TacticalComintEntity();
            c.setId(String.format("COM-%04d", 2000 + i));
            c.setVeriKaynagi(veriKaynaklari[rnd.nextInt(veriKaynaklari.length)]);
            c.setKuvvetSiraNo(String.format("KUV-COM-%04d", 8000 + i));
            c.setGoreviIcraEdenUstBirlik("1. Taktik Muhabere EH Tabur Komutanlığı");
            c.setGoreviIcraEdenEhUnsuru(comintSensors[rnd.nextInt(comintSensors.length)]);

            double ehLat = 36.5 + (rnd.nextDouble() * 5.0);
            double ehLon = 26.5 + (rnd.nextDouble() * 14.0);
            c.setEhUnsuruEnlem(ehLat);
            c.setEhUnsuruBoylam(ehLon);
            c.setEhUnsuruIrtifa(50.0 + rnd.nextInt(5000));

            double targetLat = ehLat + (rnd.nextDouble() - 0.5) * 1.2;
            double targetLon = ehLon + (rnd.nextDouble() - 0.5) * 1.2;
            c.setYayinEnlem(targetLat);
            c.setYayinBoylam(targetLon);

            double dLat = targetLat - ehLat;
            double dLon = targetLon - ehLon;
            double bearing = (Math.toDegrees(Math.atan2(dLon, dLat)) + 360.0) % 360.0;
            c.setYon(bearing);

            c.setYayinSemiMajorMeters(1500.0 + rnd.nextInt(5000));
            c.setYayinSemiMinorMeters(600.0 + rnd.nextInt(2000));
            c.setYayinOrientationDegrees((double) rnd.nextInt(180));
            c.setHedefYerBilgisi(comLocations[rnd.nextInt(comLocations.length)]);
            c.setHedefKaynagi(kaynakList[rnd.nextInt(kaynakList.length)]);
            c.setVeriGirisiYapanBirlik("CESM Operasyon Merkezi");

            String[] pair = callsignPairs[rnd.nextInt(callsignPairs.length)];
            c.setCagriAdi(pair[0]);
            c.setKarsiCagriAdi(pair[1]);
            c.setLisan(pair[2]);
            c.setTeshisKimlik(TeshisKimlik.valueOf(pair[3]));
            c.setTip(MuhabereBantTipi.valueOf(pair[4]));
            c.setProtokol(MuhabereProtokol.valueOf(pair[5]));
            c.setHaberlesmeSekli(HaberlesmeSekli.valueOf(pair[6]));

            c.setModulasyon(c.getTip() == MuhabereBantTipi.HF ? (rnd.nextBoolean() ? MuhabereModulasyon.USB : MuhabereModulasyon.LSB) : modList[rnd.nextInt(modList.length)]);
            c.setCalismaSekli(c.getCagriAdi().contains("ROLE") || c.getKarsiCagriAdi().contains("ROLE") ? CalismaSekli.ROLE : calismaList[rnd.nextInt(calismaList.length)]);
            c.setMti(rnd.nextBoolean()); // Hareketli araç telsizi mi?
            c.setGenlikDbm(-90.0 + rnd.nextDouble() * 55.0);
            c.setAltEsikSeviyesiDbm(-95.0);

            if (c.getTip() == MuhabereBantTipi.HF) {
                c.setBantGenisligiHz(3000.0 + rnd.nextInt(3000));
                double baseHf = 3.5 + rnd.nextDouble() * 22.0; // 3.5 - 25.5 MHz
                List<MuhabereFrekansEntry> fEntries = List.of(
                    new MuhabereFrekansEntry(rnd.nextBoolean() ? MuhabereFrekansTipi.SABIT : MuhabereFrekansTipi.ATLAMALI, baseHf, baseHf + 0.1)
                );
                c.setMinFrekansMhz(baseHf);
                c.setMaxFrekansMhz(baseHf + 0.1);
                c.setFrekansListesiJson(objectMapper.writeValueAsString(fEntries));
            } else {
                c.setBantGenisligiHz(12500.0 + (rnd.nextBoolean() ? 0 : 12500.0));
                double baseVhf = 35.0 + rnd.nextDouble() * 180.0; // 35 - 215 MHz VHF
                boolean isHopping = rnd.nextBoolean() || c.getHaberlesmeSekli() == HaberlesmeSekli.KRIPTO;
                List<MuhabereFrekansEntry> fEntries = new ArrayList<>();
                fEntries.add(new MuhabereFrekansEntry(isHopping ? MuhabereFrekansTipi.ATLAMALI : MuhabereFrekansTipi.SABIT, baseVhf, isHopping ? baseVhf + 15.0 : baseVhf + 0.025));
                c.setMinFrekansMhz(baseVhf);
                c.setMaxFrekansMhz(isHopping ? baseVhf + 15.0 : baseVhf + 0.025);
                c.setFrekansListesiJson(objectMapper.writeValueAsString(fEntries));
            }

            Instant now = Instant.now().minus(rnd.nextInt(48), ChronoUnit.HOURS);
            c.setSonTespitZamani(now);
            c.setIlkTespitZamani(now.minus(rnd.nextInt(1800) + 60, ChronoUnit.SECONDS));
            c.setSureSn(10.0 + rnd.nextInt(600));

            if (c.getHaberlesmeSekli() == HaberlesmeSekli.KARISTIRMA_GAYDA) {
                c.setOperatorNotu("Düşman karıştırma kaynağı taktik VHF kanalına Gayda karıştırması uyguluyor. Bastırma etkisi yüksek.");
                c.setOnaylayanKullanici("Bnb. Tolga Akın");
                c.setOnayNotu("Telsiz Karıştırma Tehdidi Kaydedildi.");
            } else if (c.getHaberlesmeSekli() == HaberlesmeSekli.DARBE_PATLAMA) {
                c.setOperatorNotu("Kısa süreli darbe patlama (Burst) sinyali tespit edildi. İstihbari gizli haberleşme şüphesi mevcut.");
                c.setOnaylayanKullanici("Yzb. Levent Korkmaz");
                c.setOnayNotu("Analiz İçin Sinyal İstihbarat Merkezine İletildi.");
            } else if (c.getHaberlesmeSekli() == HaberlesmeSekli.KRIPTO) {
                c.setOperatorNotu("DMR Sayısal kriptolu haberleşme. Çağrı adları üzerinden komuta kontrol linki kuruldu.");
                c.setOnaylayanKullanici("Yzb. Can Demir");
                c.setOnayNotu("Hedef Kütüphanesine Eklendi.");
            } else {
                c.setOperatorNotu("Rutin telsiz kestirim ve dinleme kaydı.");
                c.setOnaylayanKullanici("Ütğm. Murat Kaya");
                c.setOnayNotu("Doğrulandı.");
            }
            comints.add(c);
        }

        comintRepo.saveAll(comints);
        log.info("✅ [DATA SEEDER] 120 adet Taktik Muhabere (COMINT) yayını başarıyla PostgreSQL'e kaydedildi.");
    }
}
