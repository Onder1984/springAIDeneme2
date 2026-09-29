package com.c2.ew.infrastructure.entity;

import com.c2.ew.domain.model.EmitterActivity;
import com.c2.ew.domain.model.EmitterFix;
import com.c2.ew.domain.model.ErrorEllipse;
import com.c2.ew.domain.model.GeoPoint;
import com.c2.ew.domain.model.RfSignature;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;

/**
 * İlişkisel Veritabanı EmitterFix Tablosu
 */
@Entity
@Table(name = "emitter_fixes", indexes = {
    @Index(name = "idx_fix_band_status", columnList = "band, status"),
    @Index(name = "idx_fix_threat", columnList = "threat_level DESC"),
    @Index(name = "idx_fix_last_seen", columnList = "last_seen DESC"),
    @Index(name = "idx_fix_freq", columnList = "frequency_mhz"),
    @Index(name = "idx_fix_radar_type", columnList = "radar_type"),
    @Index(name = "idx_fix_platform", columnList = "platform_type")
})
public class EmitterFixEntity {

    @Id
    @Column(name = "fix_id", length = 32, nullable = false)
    private String fixId;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private EmitterActivity status;

    @Column(name = "band", length = 8, nullable = false)
    private String band;

    @Column(name = "frequency_mhz", nullable = false)
    private double frequencyMhz;

    @Column(name = "pri_us")
    private Double priUs;

    @Column(name = "pulse_width_us")
    private Double pulseWidthUs;

    @Column(name = "modulation", length = 32)
    private String modulation;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "altitude_meters")
    private Double altitudeMeters;

    @Column(name = "semi_major_axis_meters", nullable = false)
    private double semiMajorAxisMeters;

    @Column(name = "semi_minor_axis_meters", nullable = false)
    private double semiMinorAxisMeters;

    @Column(name = "orientation_degrees", nullable = false)
    private double orientationDegrees;

    @Column(name = "confidence_percent", nullable = false)
    private int confidencePercent;

    @Column(name = "source_lob_count", nullable = false)
    private int sourceLobCount;

    @Column(name = "threat_level", nullable = false)
    private int threatLevel; // 1 (Düşük) - 5 (Kritik Atış Kontrol / Füze Güdüm)

    @Column(name = "radar_type", length = 64)
    private String radarType;

    @Column(name = "platform_type", length = 32)
    private String platformType;

    public EmitterFixEntity() {}

    public EmitterFixEntity(String fixId, Instant firstSeen, Instant lastSeen, EmitterActivity status,
                            String band, double frequencyMhz, Double priUs, Double pulseWidthUs,
                            String modulation, double latitude, double longitude, Double altitudeMeters,
                            double semiMajorAxisMeters, double semiMinorAxisMeters,
                            double orientationDegrees, int confidencePercent, int sourceLobCount,
                            int threatLevel) {
        this(fixId, firstSeen, lastSeen, status, band, frequencyMhz, priUs, pulseWidthUs,
             modulation, latitude, longitude, altitudeMeters, semiMajorAxisMeters, semiMinorAxisMeters,
             orientationDegrees, confidencePercent, sourceLobCount, threatLevel, "Bilinmeyen Radar", "LAND_MOBILE");
    }

    public EmitterFixEntity(String fixId, Instant firstSeen, Instant lastSeen, EmitterActivity status,
                            String band, double frequencyMhz, Double priUs, Double pulseWidthUs,
                            String modulation, double latitude, double longitude, Double altitudeMeters,
                            double semiMajorAxisMeters, double semiMinorAxisMeters,
                            double orientationDegrees, int confidencePercent, int sourceLobCount,
                            int threatLevel, String radarType, String platformType) {
        this.fixId = fixId;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.status = status;
        this.band = band;
        this.frequencyMhz = frequencyMhz;
        this.priUs = priUs;
        this.pulseWidthUs = pulseWidthUs;
        this.modulation = modulation;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitudeMeters = altitudeMeters;
        this.semiMajorAxisMeters = semiMajorAxisMeters;
        this.semiMinorAxisMeters = semiMinorAxisMeters;
        this.orientationDegrees = orientationDegrees;
        this.confidencePercent = confidencePercent;
        this.sourceLobCount = sourceLobCount;
        this.threatLevel = threatLevel;
        this.radarType = radarType;
        this.platformType = platformType;
    }

    /**
     * Entity'yi Domain EmitterFix nesnesine dönüştürür.
     */
    public EmitterFix toDomain() {
        return new EmitterFix(
            fixId,
            firstSeen,
            lastSeen,
            new GeoPoint(latitude, longitude, altitudeMeters),
            new ErrorEllipse(semiMajorAxisMeters, semiMinorAxisMeters, orientationDegrees, confidencePercent),
            new RfSignature(frequencyMhz, pulseWidthUs, priUs, band, modulation),
            Collections.emptyList(),
            status,
            radarType != null ? radarType : "Bilinmeyen Radar",
            platformType != null ? platformType : "LAND_MOBILE"
        );
    }

    // Getters and Setters
    public String getFixId() { return fixId; }
    public void setFixId(String fixId) { this.fixId = fixId; }

    public Instant getFirstSeen() { return firstSeen; }
    public void setFirstSeen(Instant firstSeen) { this.firstSeen = firstSeen; }

    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }

    public EmitterActivity getStatus() { return status; }
    public void setStatus(EmitterActivity status) { this.status = status; }

    public String getBand() { return band; }
    public void setBand(String band) { this.band = band; }

    public double getFrequencyMhz() { return frequencyMhz; }
    public void setFrequencyMhz(double frequencyMhz) { this.frequencyMhz = frequencyMhz; }

    public Double getPriUs() { return priUs; }
    public void setPriUs(Double priUs) { this.priUs = priUs; }

    public Double getPulseWidthUs() { return pulseWidthUs; }
    public void setPulseWidthUs(Double pulseWidthUs) { this.pulseWidthUs = pulseWidthUs; }

    public String getModulation() { return modulation; }
    public void setModulation(String modulation) { this.modulation = modulation; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public Double getAltitudeMeters() { return altitudeMeters; }
    public void setAltitudeMeters(Double altitudeMeters) { this.altitudeMeters = altitudeMeters; }

    public double getSemiMajorAxisMeters() { return semiMajorAxisMeters; }
    public void setSemiMajorAxisMeters(double semiMajorAxisMeters) { this.semiMajorAxisMeters = semiMajorAxisMeters; }

    public double getSemiMinorAxisMeters() { return semiMinorAxisMeters; }
    public void setSemiMinorAxisMeters(double semiMinorAxisMeters) { this.semiMinorAxisMeters = semiMinorAxisMeters; }

    public double getOrientationDegrees() { return orientationDegrees; }
    public void setOrientationDegrees(double orientationDegrees) { this.orientationDegrees = orientationDegrees; }

    public int getConfidencePercent() { return confidencePercent; }
    public void setConfidencePercent(int confidencePercent) { this.confidencePercent = confidencePercent; }

    public int getSourceLobCount() { return sourceLobCount; }
    public void setSourceLobCount(int sourceLobCount) { this.sourceLobCount = sourceLobCount; }

    public int getThreatLevel() { return threatLevel; }
    public void setThreatLevel(int threatLevel) { this.threatLevel = threatLevel; }

    public String getRadarType() { return radarType; }
    public void setRadarType(String radarType) { this.radarType = radarType; }

    public String getPlatformType() { return platformType; }
    public void setPlatformType(String platformType) { this.platformType = platformType; }
}
