package com.c2.ew.infrastructure.entity;

import com.c2.ew.domain.enums.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tactical_emissions", indexes = {
    @Index(name = "idx_emi_teshis", columnList = "teshis_kimlik"),
    @Index(name = "idx_emi_veri_kaynagi", columnList = "veri_kaynagi"),
    @Index(name = "idx_emi_radar_adi", columnList = "radar_adi"),
    @Index(name = "idx_emi_platform_ortami", columnList = "platform_ortami"),
    @Index(name = "idx_emi_taciz", columnList = "taciz"),
    @Index(name = "idx_emi_et_durumu", columnList = "et_uygulama_durumu"),
    @Index(name = "idx_emi_freq_range", columnList = "min_frekans_mhz, max_frekans_mhz"),
    @Index(name = "idx_emi_pri_range", columnList = "min_pri_micro_sec, max_pri_micro_sec"),
    @Index(name = "idx_emi_pw_range", columnList = "min_pw_micro_sec, max_pw_micro_sec"),
    @Index(name = "idx_emi_atp_range", columnList = "min_atp_micro_sec, max_atp_micro_sec"),
    @Index(name = "idx_emi_son_tespit", columnList = "son_tespit_zamani")
})
public class TacticalEmissionEntity {

    @Id
    @Column(name = "emission_id", length = 64)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "veri_kaynagi", length = 32, nullable = false)
    private VeriKaynagi veriKaynagi;

    @Column(name = "kuvvet_sira_no", length = 64)
    private String kuvvetSiraNo;

    @Column(name = "ust_birlik_ref", length = 128)
    private String ustBirlikRef;

    @Column(name = "eh_unsuru", length = 128)
    private String ehUnsuru;

    @Column(name = "yon")
    private Double yon; // Derece (0-360)

    @Column(name = "eh_unsuru_enlem")
    private Double ehUnsuruEnlem;

    @Column(name = "eh_unsuru_boylam")
    private Double ehUnsuruBoylam;

    @Column(name = "eh_unsuru_irtifa")
    private Double ehUnsuruIrtifa;

    // Yayın konumu (Elips Nesnesi)
    @Column(name = "yayin_enlem")
    private Double yayinEnlem;

    @Column(name = "yayin_boylam")
    private Double yayinBoylam;

    @Column(name = "yayin_semi_major_meters")
    private Double yayinSemiMajorMeters;

    @Column(name = "yayin_semi_minor_meters")
    private Double yayinSemiMinorMeters;

    @Column(name = "yayin_orientation_degrees")
    private Double yayinOrientationDegrees;

    @Column(name = "hedef_yer_bilgisi", length = 256)
    private String hedefYerBilgisi;

    @Column(name = "hedef_mevzi_bilgisi", length = 128)
    private String hedefMevziBilgisi;

    @Enumerated(EnumType.STRING)
    @Column(name = "teshis_kimlik", length = 32, nullable = false)
    private TeshisKimlik teshisKimlik;

    @Column(name = "elint_notasyonu", length = 64)
    private String elintNotasyonu;

    @Enumerated(EnumType.STRING)
    @Column(name = "hedef_kaynagi", length = 32)
    private HedefKaynagi hedefKaynagi;

    @Enumerated(EnumType.STRING)
    @Column(name = "polarizasyon", length = 32)
    private Polarizasyon polarizasyon;

    @Column(name = "spot_no", length = 6)
    private String spotNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "pulse_cw", length = 32)
    private PulseCw pulseCw;

    @Column(name = "radar_gorevi", length = 128)
    private String radarGorevi;

    @Column(name = "radar_adi", length = 128)
    private String radarAdi;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_ortami", length = 32)
    private PlatformOrtami platformOrtami;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_tipi", length = 32)
    private PlatformTipi platformTipi;

    @Column(name = "genlik_dbm")
    private Double genlikDbm;

    @Enumerated(EnumType.STRING)
    @Column(name = "modulasyon", length = 64)
    private Modulasyon modulasyon;

    @Enumerated(EnumType.STRING)
    @Column(name = "et_uygulama_durumu", length = 32)
    private EtUygulamaDurumu etUygulamaDurumu;

    @Column(name = "ilk_tespit_zamani")
    private Instant ilkTespitZamani;

    @Column(name = "son_tespit_zamani")
    private Instant sonTespitZamani;

    @Column(name = "sure_sn")
    private Double sureSn;

    @Column(name = "hss", length = 128)
    private String hss;

    @Column(name = "taciz")
    private Boolean taciz;

    @Column(name = "radar_kesit_alani")
    private Double radarKesitAlani;

    @Column(name = "radar_iz_numarasi", length = 64)
    private String radarIzNumarasi;

    @Column(name = "ucak_kuyruk_numarasi", length = 64)
    private String ucakKuyrukNumarasi;

    // Hızlı B-Tree aralıklı sorgu zarf kolonları (Range Envelopes)
    @Column(name = "min_frekans_mhz")
    private Double minFrekansMhz;

    @Column(name = "max_frekans_mhz")
    private Double maxFrekansMhz;

    @Column(name = "min_pri_micro_sec")
    private Double minPriMicroSec;

    @Column(name = "max_pri_micro_sec")
    private Double maxPriMicroSec;

    @Column(name = "min_pw_micro_sec")
    private Double minPwMicroSec;

    @Column(name = "max_pw_micro_sec")
    private Double maxPwMicroSec;

    @Column(name = "min_atp_micro_sec")
    private Double minAtpMicroSec;

    @Column(name = "max_atp_micro_sec")
    private Double maxAtpMicroSec;

    // Çoklu Listeler (JSON formatında saklanır)
    @Column(name = "frekans_listesi_json", columnDefinition = "text")
    private String frekansListesiJson;

    @Column(name = "pri_listesi_json", columnDefinition = "text")
    private String priListesiJson;

    @Column(name = "pw_listesi_json", columnDefinition = "text")
    private String pwListesiJson;

    @Column(name = "atp_listesi_json", columnDefinition = "text")
    private String atpListesiJson;

    @Column(name = "operator_notu", columnDefinition = "text")
    private String operatorNotu;

    @Column(name = "onaylayan_kullanici", length = 128)
    private String onaylayanKullanici;

    @Column(name = "onay_notu", columnDefinition = "text")
    private String onayNotu;

    public TacticalEmissionEntity() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public VeriKaynagi getVeriKaynagi() { return veriKaynagi; }
    public void setVeriKaynagi(VeriKaynagi veriKaynagi) { this.veriKaynagi = veriKaynagi; }

    public String getKuvvetSiraNo() { return kuvvetSiraNo; }
    public void setKuvvetSiraNo(String kuvvetSiraNo) { this.kuvvetSiraNo = kuvvetSiraNo; }

    public String getUstBirlikRef() { return ustBirlikRef; }
    public void setUstBirlikRef(String ustBirlikRef) { this.ustBirlikRef = ustBirlikRef; }

    public String getEhUnsuru() { return ehUnsuru; }
    public void setEhUnsuru(String ehUnsuru) { this.ehUnsuru = ehUnsuru; }

    public Double getYon() { return yon; }
    public void setYon(Double yon) { this.yon = yon; }

    public Double getEhUnsuruEnlem() { return ehUnsuruEnlem; }
    public void setEhUnsuruEnlem(Double ehUnsuruEnlem) { this.ehUnsuruEnlem = ehUnsuruEnlem; }

    public Double getEhUnsuruBoylam() { return ehUnsuruBoylam; }
    public void setEhUnsuruBoylam(Double ehUnsuruBoylam) { this.ehUnsuruBoylam = ehUnsuruBoylam; }

    public Double getEhUnsuruIrtifa() { return ehUnsuruIrtifa; }
    public void setEhUnsuruIrtifa(Double ehUnsuruIrtifa) { this.ehUnsuruIrtifa = ehUnsuruIrtifa; }

    public Double getYayinEnlem() { return yayinEnlem; }
    public void setYayinEnlem(Double yayinEnlem) { this.yayinEnlem = yayinEnlem; }

    public Double getYayinBoylam() { return yayinBoylam; }
    public void setYayinBoylam(Double yayinBoylam) { this.yayinBoylam = yayinBoylam; }

    public Double getYayinSemiMajorMeters() { return yayinSemiMajorMeters; }
    public void setYayinSemiMajorMeters(Double yayinSemiMajorMeters) { this.yayinSemiMajorMeters = yayinSemiMajorMeters; }

    public Double getYayinSemiMinorMeters() { return yayinSemiMinorMeters; }
    public void setYayinSemiMinorMeters(Double yayinSemiMinorMeters) { this.yayinSemiMinorMeters = yayinSemiMinorMeters; }

    public Double getYayinOrientationDegrees() { return yayinOrientationDegrees; }
    public void setYayinOrientationDegrees(Double yayinOrientationDegrees) { this.yayinOrientationDegrees = yayinOrientationDegrees; }

    public String getHedefYerBilgisi() { return hedefYerBilgisi; }
    public void setHedefYerBilgisi(String hedefYerBilgisi) { this.hedefYerBilgisi = hedefYerBilgisi; }

    public String getHedefMevziBilgisi() { return hedefMevziBilgisi; }
    public void setHedefMevziBilgisi(String hedefMevziBilgisi) { this.hedefMevziBilgisi = hedefMevziBilgisi; }

    public TeshisKimlik getTeshisKimlik() { return teshisKimlik; }
    public void setTeshisKimlik(TeshisKimlik teshisKimlik) { this.teshisKimlik = teshisKimlik; }

    public String getElintNotasyonu() { return elintNotasyonu; }
    public void setElintNotasyonu(String elintNotasyonu) { this.elintNotasyonu = elintNotasyonu; }

    public HedefKaynagi getHedefKaynagi() { return hedefKaynagi; }
    public void setHedefKaynagi(HedefKaynagi hedefKaynagi) { this.hedefKaynagi = hedefKaynagi; }

    public Polarizasyon getPolarizasyon() { return polarizasyon; }
    public void setPolarizasyon(Polarizasyon polarizasyon) { this.polarizasyon = polarizasyon; }

    public String getSpotNo() { return spotNo; }
    public void setSpotNo(String spotNo) { this.spotNo = spotNo; }

    public PulseCw getPulseCw() { return pulseCw; }
    public void setPulseCw(PulseCw pulseCw) { this.pulseCw = pulseCw; }

    public String getRadarGorevi() { return radarGorevi; }
    public void setRadarGorevi(String radarGorevi) { this.radarGorevi = radarGorevi; }

    public String getRadarAdi() { return radarAdi; }
    public void setRadarAdi(String radarAdi) { this.radarAdi = radarAdi; }

    public PlatformOrtami getPlatformOrtami() { return platformOrtami; }
    public void setPlatformOrtami(PlatformOrtami platformOrtami) { this.platformOrtami = platformOrtami; }

    public PlatformTipi getPlatformTipi() { return platformTipi; }
    public void setPlatformTipi(PlatformTipi platformTipi) { this.platformTipi = platformTipi; }

    public Double getGenlikDbm() { return genlikDbm; }
    public void setGenlikDbm(Double genlikDbm) { this.genlikDbm = genlikDbm; }

    public Modulasyon getModulasyon() { return modulasyon; }
    public void setModulasyon(Modulasyon modulasyon) { this.modulasyon = modulasyon; }

    public EtUygulamaDurumu getEtUygulamaDurumu() { return etUygulamaDurumu; }
    public void setEtUygulamaDurumu(EtUygulamaDurumu etUygulamaDurumu) { this.etUygulamaDurumu = etUygulamaDurumu; }

    public Instant getIlkTespitZamani() { return ilkTespitZamani; }
    public void setIlkTespitZamani(Instant ilkTespitZamani) { this.ilkTespitZamani = ilkTespitZamani; }

    public Instant getSonTespitZamani() { return sonTespitZamani; }
    public void setSonTespitZamani(Instant sonTespitZamani) { this.sonTespitZamani = sonTespitZamani; }

    public Double getSureSn() { return sureSn; }
    public void setSureSn(Double sureSn) { this.sureSn = sureSn; }

    public String getHss() { return hss; }
    public void setHss(String hss) { this.hss = hss; }

    public Boolean getTaciz() { return taciz; }
    public void setTaciz(Boolean taciz) { this.taciz = taciz; }

    public Double getRadarKesitAlani() { return radarKesitAlani; }
    public void setRadarKesitAlani(Double radarKesitAlani) { this.radarKesitAlani = radarKesitAlani; }

    public String getRadarIzNumarasi() { return radarIzNumarasi; }
    public void setRadarIzNumarasi(String radarIzNumarasi) { this.radarIzNumarasi = radarIzNumarasi; }

    public String getUcakKuyrukNumarasi() { return ucakKuyrukNumarasi; }
    public void setUcakKuyrukNumarasi(String ucakKuyrukNumarasi) { this.ucakKuyrukNumarasi = ucakKuyrukNumarasi; }

    public Double getMinFrekansMhz() { return minFrekansMhz; }
    public void setMinFrekansMhz(Double minFrekansMhz) { this.minFrekansMhz = minFrekansMhz; }

    public Double getMaxFrekansMhz() { return maxFrekansMhz; }
    public void setMaxFrekansMhz(Double maxFrekansMhz) { this.maxFrekansMhz = maxFrekansMhz; }

    public Double getMinPriMicroSec() { return minPriMicroSec; }
    public void setMinPriMicroSec(Double minPriMicroSec) { this.minPriMicroSec = minPriMicroSec; }

    public Double getMaxPriMicroSec() { return maxPriMicroSec; }
    public void setMaxPriMicroSec(Double maxPriMicroSec) { this.maxPriMicroSec = maxPriMicroSec; }

    public Double getMinPwMicroSec() { return minPwMicroSec; }
    public void setMinPwMicroSec(Double minPwMicroSec) { this.minPwMicroSec = minPwMicroSec; }

    public Double getMaxPwMicroSec() { return maxPwMicroSec; }
    public void setMaxPwMicroSec(Double maxPwMicroSec) { this.maxPwMicroSec = maxPwMicroSec; }

    public Double getMinAtpMicroSec() { return minAtpMicroSec; }
    public void setMinAtpMicroSec(Double minAtpMicroSec) { this.minAtpMicroSec = minAtpMicroSec; }

    public Double getMaxAtpMicroSec() { return maxAtpMicroSec; }
    public void setMaxAtpMicroSec(Double maxAtpMicroSec) { this.maxAtpMicroSec = maxAtpMicroSec; }

    public String getFrekansListesiJson() { return frekansListesiJson; }
    public void setFrekansListesiJson(String frekansListesiJson) { this.frekansListesiJson = frekansListesiJson; }

    public String getPriListesiJson() { return priListesiJson; }
    public void setPriListesiJson(String priListesiJson) { this.priListesiJson = priListesiJson; }

    public String getPwListesiJson() { return pwListesiJson; }
    public void setPwListesiJson(String pwListesiJson) { this.pwListesiJson = pwListesiJson; }

    public String getAtpListesiJson() { return atpListesiJson; }
    public void setAtpListesiJson(String atpListesiJson) { this.atpListesiJson = atpListesiJson; }

    public String getOperatorNotu() { return operatorNotu; }
    public void setOperatorNotu(String operatorNotu) { this.operatorNotu = operatorNotu; }

    public String getOnaylayanKullanici() { return onaylayanKullanici; }
    public void setOnaylayanKullanici(String onaylayanKullanici) { this.onaylayanKullanici = onaylayanKullanici; }

    public String getOnayNotu() { return onayNotu; }
    public void setOnayNotu(String onayNotu) { this.onayNotu = onayNotu; }
}
