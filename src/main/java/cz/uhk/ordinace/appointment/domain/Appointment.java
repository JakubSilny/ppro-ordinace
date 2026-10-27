package cz.uhk.ordinace.appointment.domain;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import cz.uhk.ordinace.common.BusinessRuleException;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "appointment")
@EntityListeners(AuditingEntityListener.class)
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long doctorId;

    private long patientId;

    private Instant startAt;

    private Instant endAt;

    @Enumerated(EnumType.STRING)
    private AppointmentStatus status = AppointmentStatus.BOOKED;

    private boolean needsRebooking;

    private String reason;

    private Long rescheduledFromId;

    private Instant updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;

    protected Appointment() {
    }

    public Appointment(long doctorId, long patientId, Instant startAt, Instant endAt, Optional<String> reason,
            Optional<Long> rescheduledFromId) {
        this.doctorId = doctorId;
        this.patientId = patientId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.reason = reason.orElse(null);
        this.rescheduledFromId = rescheduledFromId.orElse(null);
    }

    public void cancel() {
        requireBooked("cancelled");
        status = AppointmentStatus.CANCELLED;
        needsRebooking = false;
    }

    public void markNoShow(Instant now) {
        requireBooked("marked as no-show");
        if (now.isBefore(startAt)) {
            throw new BusinessRuleException("Appointment has not started yet");
        }
        status = AppointmentStatus.NO_SHOW;
    }

    /** Návštěvu lze zapsat až po začátku rezervace. */
    public void complete(Instant now) {
        requireBooked("completed");
        if (now.isBefore(startAt)) {
            throw new BusinessRuleException("Visit cannot be recorded before the appointment starts");
        }
        status = AppointmentStatus.COMPLETED;
        needsRebooking = false;
    }

    public void markRescheduled() {
        requireBooked("rescheduled");
        status = AppointmentStatus.RESCHEDULED;
        needsRebooking = false;
    }

    private void requireBooked(String action) {
        if (status != AppointmentStatus.BOOKED) {
            throw new BusinessRuleException("Only a BOOKED appointment can be " + action + ", this one is " + status);
        }
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public long getDoctorId() {
        return doctorId;
    }

    public long getPatientId() {
        return patientId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public boolean isNeedsRebooking() {
        return needsRebooking;
    }

    public Optional<String> getReason() {
        return Optional.ofNullable(reason);
    }

    public Optional<Long> getRescheduledFromId() {
        return Optional.ofNullable(rescheduledFromId);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Optional<String> getCreatedBy() {
        return Optional.ofNullable(createdBy);
    }

    public Optional<String> getUpdatedBy() {
        return Optional.ofNullable(updatedBy);
    }
}
