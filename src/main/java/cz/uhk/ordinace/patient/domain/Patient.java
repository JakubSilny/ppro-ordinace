package cz.uhk.ordinace.patient.domain;

import java.time.Instant;
import java.util.Optional;

import cz.uhk.ordinace.insurer.domain.Insurer;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "patient")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;

    private String lastName;

    private int birthYear;

    private String phone;

    private String insuranceId;

    @ManyToOne
    @JoinColumn(name = "insurer_id")
    private Insurer insurer;

    private boolean active = true;

    private Instant updatedAt;

    protected Patient() {
    }

    /** {@code insuranceId} a {@code insurer} jsou nepovinné, uložené jako NULL. */
    public Patient(String firstName, String lastName, int birthYear, String phone,
            Optional<String> insuranceId, Optional<Insurer> insurer) {
        update(firstName, lastName, birthYear, phone, insuranceId, insurer);
    }

    public final void update(String firstName, String lastName, int birthYear, String phone,
            Optional<String> insuranceId, Optional<Insurer> insurer) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthYear = birthYear;
        this.phone = phone;
        this.insuranceId = insuranceId.orElse(null);
        this.insurer = insurer.orElse(null);
    }

    public void deactivate() {
        this.active = false;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public int getBirthYear() {
        return birthYear;
    }

    public String getPhone() {
        return phone;
    }

    public Optional<String> getInsuranceId() {
        return Optional.ofNullable(insuranceId);
    }

    public Optional<Insurer> getInsurer() {
        return Optional.ofNullable(insurer);
    }

    public boolean isActive() {
        return active;
    }
}
