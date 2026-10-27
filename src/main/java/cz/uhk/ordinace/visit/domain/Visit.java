package cz.uhk.ordinace.visit.domain;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "visit")
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long appointmentId;

    private String note;

    private Instant createdAt;

    protected Visit() {
    }

    public Visit(long appointmentId, Optional<String> note, Instant createdAt) {
        this.appointmentId = appointmentId;
        this.note = note.orElse(null);
        this.createdAt = createdAt;
    }

    public void updateNote(Optional<String> note) {
        this.note = note.orElse(null);
    }

    public Long getId() {
        return id;
    }

    public long getAppointmentId() {
        return appointmentId;
    }

    public Optional<String> getNote() {
        return Optional.ofNullable(note);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
