package cz.uhk.ordinace.visit.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cz.uhk.ordinace.visit.domain.Visit;

public interface VisitRepository extends JpaRepository<Visit, Long> {

    Optional<Visit> findByAppointmentId(long appointmentId);
}
