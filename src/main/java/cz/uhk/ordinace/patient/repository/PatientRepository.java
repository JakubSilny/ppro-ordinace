package cz.uhk.ordinace.patient.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cz.uhk.ordinace.patient.domain.Patient;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByInsuranceId(String insuranceId);

    @Query(value = """
            SELECT * FROM patient
            WHERE unaccent(lower(first_name)) = unaccent(lower(:firstName))
              AND unaccent(lower(last_name)) = unaccent(lower(:lastName))
              AND birth_year = :birthYear AND phone = :phone
            """, nativeQuery = true)
    List<Patient> findSameIdentity(@Param("firstName") String firstName, @Param("lastName") String lastName,
            @Param("birthYear") int birthYear, @Param("phone") String phone);

    @Query(value = """
            SELECT * FROM patient
            WHERE active AND unaccent(lower(last_name)) LIKE unaccent(lower(:prefix)) || '%'
            ORDER BY last_name, first_name LIMIT 20
            """, nativeQuery = true)
    List<Patient> searchByLastName(@Param("prefix") String prefix);

    @Query(value = """
            SELECT * FROM patient
            WHERE active AND phone LIKE '%' || :digits || '%'
            ORDER BY last_name, first_name LIMIT 20
            """, nativeQuery = true)
    List<Patient> searchByPhone(@Param("digits") String digits);

    @Query(value = "SELECT count(*) FROM appointment WHERE patient_id = :id AND status = 'BOOKED' AND start_at > :now",
            nativeQuery = true)
    long countUpcomingBookings(@Param("id") long id, @Param("now") Instant now);
}
