package cz.uhk.ordinace.doctor.domain;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "doctor_absence")
public class DoctorAbsence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long doctorId;

    private Instant startAt;

    private Instant endAt;

    private String reason;

    protected DoctorAbsence() {
    }

    public DoctorAbsence(long doctorId, Instant startAt, Instant endAt, Optional<String> reason) {
        this.doctorId = doctorId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.reason = reason.orElse(null);
    }

    public Long getId() {
        return id;
    }

    public long getDoctorId() {
        return doctorId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public Optional<String> getReason() {
        return Optional.ofNullable(reason);
    }
}
