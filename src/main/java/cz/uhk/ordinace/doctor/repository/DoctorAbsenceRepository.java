package cz.uhk.ordinace.doctor.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cz.uhk.ordinace.doctor.domain.DoctorAbsence;

public interface DoctorAbsenceRepository extends JpaRepository<DoctorAbsence, Long> {

    List<DoctorAbsence> findByDoctorIdAndEndAtAfterOrderByStartAt(long doctorId, Instant after);

    boolean existsByDoctorIdAndStartAtLessThanAndEndAtGreaterThan(long doctorId, Instant end, Instant start);

    List<DoctorAbsence> findByDoctorIdAndStartAtLessThanAndEndAtGreaterThanOrderByStartAt(long doctorId, Instant end,
            Instant start);

    /** Označí aktivní (BOOKED) rezervace v době absence k přeobjednání; vrací jejich počet. */
    @Modifying
    @Query(value = """
            UPDATE appointment SET needs_rebooking = TRUE, updated_at = now()
            WHERE doctor_id = :doctorId AND status = 'BOOKED' AND start_at < :end AND end_at > :start
            """, nativeQuery = true)
    int flagForRebooking(@Param("doctorId") long doctorId, @Param("start") Instant start, @Param("end") Instant end);
}
