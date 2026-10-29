package cz.uhk.ordinace.doctor.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cz.uhk.ordinace.doctor.domain.Doctor;
import jakarta.persistence.LockModeType;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    /** Zamkne řádek do konce transakce, aby se objednání, absence a vyřazení lékaře nepředběhly. */
    // ponytail: objednávky téhož lékaře jdou za sebou; u jedné ordinace nevadí, jinak advisory lock na interval
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Doctor> findLockedById(long id);

    List<Doctor> findByActiveTrueOrderByLastNameAscFirstNameAsc();

    @Query(value = "SELECT count(*) FROM appointment WHERE doctor_id = :id AND status = 'BOOKED' AND end_at > :now",
            nativeQuery = true)
    long countUpcomingBookings(@Param("id") long id, @Param("now") Instant now);
}
