package cz.uhk.ordinace.doctor.domain;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "doctor")
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;

    private String lastName;

    private boolean active = true;

    private Instant updatedAt;

    protected Doctor() {
    }

    public Doctor(String firstName, String lastName) {
        update(firstName, lastName);
    }

    public final void update(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
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

    public boolean isActive() {
        return active;
    }
}
