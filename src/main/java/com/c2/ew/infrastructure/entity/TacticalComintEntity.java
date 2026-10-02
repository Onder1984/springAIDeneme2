package com.c2.ew.infrastructure.entity;

import com.c2.ew.domain.enums.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tactical_comint", indexes = {
    @Index(name = "idx_com_teshis", columnList = "teshis_kimlik"),
    @Index(name = "idx_com_veri_kaynagi", columnList = "veri_kaynagi"),
    @Index(name = "idx_com_lisan", columnList = "lisan"),
    @Index(name = "idx_com_protokol", columnList = "protokol"),
    @Index(name = "idx_com_modulasyon", columnList = "modulasyon"),
    @Index(name = "idx_com_tip", columnList = "tip"),
    @Index(name = "idx_com_haberlesme_sekli", columnList = "haberlesme_sekli"),
    @Index(name = "idx_com_cagri_adi", columnList = "cagri_adi"),
    @Index(name = "idx_com_karsi_cagri_adi", columnList = "karsi_cagri_adi"),
    @Index(name = "idx_com_freq_range", columnList = "min_frekans_mhz, max_frekans_mhz"),
    @Index(name = "idx_com_son_tespit", columnList = "son_tespit_zamani")
})
public class TacticalComintEntity {

    @Id
    @Column(name = "comint_id", length = 64)
    private String id; // Örn: COM-2001

    @Enumerated(EnumType.STRING)
    @Column(name = "veri_kaynagi", length = 32, nullable = false)
    private VeriKaynagi veriKaynagi;

    @Column(name = "kuvvet_sira_no", length = 64)
    private String kuvvetSiraNo;

    @Column(name = "ust_birlik_ref", length = 128)
    private String goreviIcraEdenUstBirlik;

    @Column(name = "eh_unsuru", length = 128)
    private String goreviIcraEdenEhUnsuru;

    @Column(name = "yon")
    private Double yon; // Kerteriz Derecesi (0-360)

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

    @Enumerated(EnumType.STRING)
    @Column(name = "hedef_kaynagi", length = 32)
    private HedefKaynagi hedefKaynagi;

    @Column(name = "veri_girisi_yapan_birlik", length = 50)
    private String veriGirisiYapanBirlik;

    @Column(name = "lisan", length = 64)
    private String lisan;

    @Enumerated(EnumType.STRING)
    @Column(name = "protokol", length = 32)
    private MuhabereProtokol protokol;

    @Column(name = "bant_genisligi_hz")
    private Double bantGenisligiHz;

    @Enumerated(EnumType.STRING)
    @Column(name = "modulasyon", length = 32)
    private MuhabereModulasyon modulasyon;

    @Column(name = "mti")
    private Boolean mti; // Moving Target Indicator

    @Enumerated(EnumType.STRING)
    @Column(name = "tip", length = 32)
    private MuhabereBantTipi tip; // HF, VHF, TANIMSIZ

    @Column(name = "genlik_dbm")
    private Double genlikDbm;

    @Enumerated(EnumType.STRING)
    @Column(name = "haberlesme_sekli", length = 32)
    private HaberlesmeSekli haberlesmeSekli;

    @Enumerated(EnumType.STRING)
    @Column(name = "calisma_sekli", length = 32)
    private CalismaSekli calismaSekli;

    @Column(name = "alt_esik_seviyesi_dbm")
    private Double altEsikSeviyesiDbm;

    @Column(name = "cagri_adi", length = 128)
    private String cagriAdi;

    @Column(name = "karsi_cagri_adi", length = 128)
    private String karsiCagriAdi;

    @Column(name = "ilk_tespit_zamani")
    private Instant ilkTespitZamani;

    @Column(name = "son_tespit_zamani")
    private Instant sonTespitZamani;

    @Column(name = "sure_sn")
    private Double sureSn;

    @Enumerated(EnumType.STRING)
    @Column(name = "teshis_kimlik", length = 32, nullable = false)
    private TeshisKimlik teshisKimlik;

    // Hızlı B-Tree aralıklı frekans zarf kolonları (Range Envelopes)
    @Column(name = "min_frekans_mhz")
    private Double minFrekansMhz;

    @Column(name = "max_frekans_mhz")
    private Double maxFrekansMhz;

    // Çoklu Frekans Listesi (JSON formatında)
    @Column(name = "frekans_listesi_json", columnDefinition = "text")
    private String frekansListesiJson;

    @Column(name = "operator_notu", columnDefinition = "text")
    private String operatorNotu;

    @Column(name = "onaylayan_kullanici", length = 128)
    private String onaylayanKullanici;

    @Column(name = "onay_notu", columnDefinition = "text")
    private String onayNotu;

    public TacticalComintEntity() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public VeriKaynagi getVeriKaynagi() { return veriKaynagi; }
    public void setVeriKaynagi(VeriKaynagi veriKaynagi) { this.veriKaynagi = veriKaynagi; }

    public String getKuvvetSiraNo() { return kuvvetSiraNo; }
    public void setKuvvetSiraNo(String kuvvetSiraNo) { this.kuvvetSiraNo = kuvvetSiraNo; }

    public String getGoreviIcraEdenUstBirlik() { return goreviIcraEdenUstBirlik; }
    public void setGoreviIcraEdenUstBirlik(String goreviIcraEdenUstBirlik) { this.goreviIcraEdenUstBirlik = goreviIcraEdenUstBirlik; }

    public String getGoreviIcraEdenEhUnsuru() { return goreviIcraEdenEhUnsuru; }
    public void setGoreviIcraEdenEhUnsuru(String goreviIcraEdenEhUnsuru) { this.goreviIcraEdenEhUnsuru = goreviIcraEdenEhUnsuru; }

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

    public HedefKaynagi getHedefKaynagi() { return hedefKaynagi; }
    public void setHedefKaynagi(HedefKaynagi hedefKaynagi) { this.hedefKaynagi = hedefKaynagi; }

    public String getVeriGirisiYapanBirlik() { return veriGirisiYapanBirlik; }
    public void setVeriGirisiYapanBirlik(String veriGirisiYapanBirlik) { this.veriGirisiYapanBirlik = veriGirisiYapanBirlik; }

    public String getLisan() { return lisan; }
    public void setLisan(String lisan) { this.lisan = lisan; }

    public MuhabereProtokol getProtokol() { return protokol; }
    public void setProtokol(MuhabereProtokol protokol) { this.protokol = protokol; }

    public Double getBantGenisligiHz() { return bantGenisligiHz; }
    public void setBantGenisligiHz(Double bantGenisligiHz) { this.bantGenisligiHz = bantGenisligiHz; }

    public MuhabereModulasyon getModulasyon() { return modulasyon; }
    public void setModulasyon(MuhabereModulasyon modulasyon) { this.modulasyon = modulasyon; }

    public Boolean getMti() { return mti; }
    public void setMti(Boolean mti) { this.mti = mti; }

    public MuhabereBantTipi getTip() { return tip; }
    public void setTip(MuhabereBantTipi tip) { this.tip = tip; }

    public Double getGenlikDbm() { return genlikDbm; }
    public void setGenlikDbm(Double genlikDbm) { this.genlikDbm = genlikDbm; }

    public HaberlesmeSekli getHaberlesmeSekli() { return haberlesmeSekli; }
    public void setHaberlesmeSekli(HaberlesmeSekli haberlesmeSekli) { this.haberlesmeSekli = haberlesmeSekli; }

    public CalismaSekli getCalismaSekli() { return calismaSekli; }
    public void setCalismaSekli(CalismaSekli calismaSekli) { this.calismaSekli = calismaSekli; }

    public Double getAltEsikSeviyesiDbm() { return altEsikSeviyesiDbm; }
    public void setAltEsikSeviyesiDbm(Double altEsikSeviyesiDbm) { this.altEsikSeviyesiDbm = altEsikSeviyesiDbm; }

    public String getCagriAdi() { return cagriAdi; }
    public void setCagriAdi(String cagriAdi) { this.cagriAdi = cagriAdi; }

    public String getKarsiCagriAdi() { return karsiCagriAdi; }
    public void setKarsiCagriAdi(String karsiCagriAdi) { this.karsiCagriAdi = karsiCagriAdi; }

    public Instant getIlkTespitZamani() { return ilkTespitZamani; }
    public void setIlkTespitZamani(Instant ilkTespitZamani) { this.ilkTespitZamani = ilkTespitZamani; }

    public Instant getSonTespitZamani() { return sonTespitZamani; }
    public void setSonTespitZamani(Instant sonTespitZamani) { this.sonTespitZamani = sonTespitZamani; }

    public Double getSureSn() { return sureSn; }
    public void setSureSn(Double sureSn) { this.sureSn = sureSn; }

    public TeshisKimlik getTeshisKimlik() { return teshisKimlik; }
    public void setTeshisKimlik(TeshisKimlik teshisKimlik) { this.teshisKimlik = teshisKimlik; }

    public Double getMinFrekansMhz() { return minFrekansMhz; }
    public void setMinFrekansMhz(Double minFrekansMhz) { this.minFrekansMhz = minFrekansMhz; }

    public Double getMaxFrekansMhz() { return maxFrekansMhz; }
    public void setMaxFrekansMhz(Double maxFrekansMhz) { this.maxFrekansMhz = maxFrekansMhz; }

    public String getFrekansListesiJson() { return frekansListesiJson; }
    public void setFrekansListesiJson(String frekansListesiJson) { this.frekansListesiJson = frekansListesiJson; }

    public String getOperatorNotu() { return operatorNotu; }
    public void setOperatorNotu(String operatorNotu) { this.operatorNotu = operatorNotu; }

    public String getOnaylayanKullanici() { return onaylayanKullanici; }
    public void setOnaylayanKullanici(String onaylayanKullanici) { this.onaylayanKullanici = onaylayanKullanici; }

    public String getOnayNotu() { return onayNotu; }
    public void setOnayNotu(String onayNotu) { this.onayNotu = onayNotu; }
}
