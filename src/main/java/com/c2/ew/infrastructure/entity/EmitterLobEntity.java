package com.c2.ew.infrastructure.entity;

import com.c2.ew.domain.model.EmitterLob;
import com.c2.ew.domain.model.GeoPoint;
import com.c2.ew.domain.model.RfSignature;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * İlişkisel Veritabanı EmitterLob Tablosu
 */
@Entity
@Table(name = "emitter_lobs", indexes = {
    @Index(name = "idx_lob_sensor_time", columnList = "sensor_node_id, timestamp DESC"),
    @Index(name = "idx_lob_band", columnList = "band"),
    @Index(name = "idx_lob_timestamp", columnList = "timestamp DESC"),
    @Index(name = "idx_lob_fix", columnList = "associated_fix_id")
})
public class EmitterLobEntity {

    @Id
    @Column(name = "lob_id", length = 64, nullable = false)
    private String lobId;

    @Column(name = "sensor_node_id", length = 32, nullable = false)
    private String sensorNodeId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "sensor_latitude", nullable = false)
    private double sensorLatitude;

    @Column(name = "sensor_longitude", nullable = false)
    private double sensorLongitude;

    @Column(name = "bearing_degrees", nullable = false)
    private double bearingDegrees;

    @Column(name = "angular_accuracy_deg", nullable = false)
    private double angularAccuracyDeg;

    @Column(name = "frequency_mhz", nullable = false)
    private double frequencyMhz;

    @Column(name = "band", length = 8, nullable = false)
    private String band;

    @Column(name = "modulation", length = 32)
    private String modulation;

    @Column(name = "pri_us")
    private Double priUs;

    @Column(name = "pulse_width_us")
    private Double pulseWidthUs;

    @Column(name = "associated_fix_id", length = 32)
    private String associatedFixId;

    public EmitterLobEntity() {}

    public EmitterLobEntity(String lobId, String sensorNodeId, Instant timestamp,
                            double sensorLatitude, double sensorLongitude,
                            double bearingDegrees, double angularAccuracyDeg,
                            double frequencyMhz, String band, String modulation,
                            Double priUs, Double pulseWidthUs, String associatedFixId) {
        this.lobId = lobId;
        this.sensorNodeId = sensorNodeId;
        this.timestamp = timestamp;
        this.sensorLatitude = sensorLatitude;
        this.sensorLongitude = sensorLongitude;
        this.bearingDegrees = bearingDegrees;
        this.angularAccuracyDeg = angularAccuracyDeg;
        this.frequencyMhz = frequencyMhz;
        this.band = band;
        this.modulation = modulation;
        this.priUs = priUs;
        this.pulseWidthUs = pulseWidthUs;
        this.associatedFixId = associatedFixId;
    }

    public EmitterLob toDomain() {
        return new EmitterLob(
            lobId,
            sensorNodeId,
            timestamp,
            new GeoPoint(sensorLatitude, sensorLongitude),
            bearingDegrees,
            angularAccuracyDeg,
            new RfSignature(frequencyMhz, pulseWidthUs, priUs, band, modulation)
        );
    }

    // Getters and Setters
    public String getLobId() { return lobId; }
    public void setLobId(String lobId) { this.lobId = lobId; }

    public String getSensorNodeId() { return sensorNodeId; }
    public void setSensorNodeId(String sensorNodeId) { this.sensorNodeId = sensorNodeId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public double getSensorLatitude() { return sensorLatitude; }
    public void setSensorLatitude(double sensorLatitude) { this.sensorLatitude = sensorLatitude; }

    public double getSensorLongitude() { return sensorLongitude; }
    public void setSensorLongitude(double sensorLongitude) { this.sensorLongitude = sensorLongitude; }

    public double getBearingDegrees() { return bearingDegrees; }
    public void setBearingDegrees(double bearingDegrees) { this.bearingDegrees = bearingDegrees; }

    public double getAngularAccuracyDeg() { return angularAccuracyDeg; }
    public void setAngularAccuracyDeg(double angularAccuracyDeg) { this.angularAccuracyDeg = angularAccuracyDeg; }

    public double getFrequencyMhz() { return frequencyMhz; }
    public void setFrequencyMhz(double frequencyMhz) { this.frequencyMhz = frequencyMhz; }

    public String getBand() { return band; }
    public void setBand(String band) { this.band = band; }

    public String getModulation() { return modulation; }
    public void setModulation(String modulation) { this.modulation = modulation; }

    public Double getPriUs() { return priUs; }
    public void setPriUs(Double priUs) { this.priUs = priUs; }

    public Double getPulseWidthUs() { return pulseWidthUs; }
    public void setPulseWidthUs(Double pulseWidthUs) { this.pulseWidthUs = pulseWidthUs; }

    public String getAssociatedFixId() { return associatedFixId; }
    public void setAssociatedFixId(String associatedFixId) { this.associatedFixId = associatedFixId; }
}
