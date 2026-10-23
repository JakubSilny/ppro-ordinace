package cz.uhk.ordinace.doctor.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cz.uhk.ordinace.doctor.domain.Doctor;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    List<Doctor> findByActiveTrueOrderByLastNameAscFirstNameAsc();

    @Query(value = "SELECT count(*) FROM appointment WHERE doctor_id = :id AND status = 'BOOKED' AND start_at > :now",
            nativeQuery = true)
    long countUpcomingBookings(@Param("id") long id, @Param("now") Instant now);
}
