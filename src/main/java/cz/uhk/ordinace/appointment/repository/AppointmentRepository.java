package cz.uhk.ordinace.appointment.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cz.uhk.ordinace.appointment.domain.Appointment;
import cz.uhk.ordinace.appointment.domain.AppointmentStatus;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    boolean existsByDoctorIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(long doctorId,
            Collection<AppointmentStatus> statuses, Instant end, Instant start);

    boolean existsByPatientIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(long patientId,
            Collection<AppointmentStatus> statuses, Instant end, Instant start);

    @Query("""
            select a from Appointment a
            where a.doctorId = :doctorId and a.status in :statuses and a.startAt >= :from and a.startAt < :to
            order by a.startAt
            """)
    List<Appointment> findDiary(@Param("doctorId") long doctorId, @Param("statuses") Collection<AppointmentStatus> statuses,
            @Param("from") Instant from, @Param("to") Instant to);

    List<Appointment> findByStatusAndNeedsRebookingTrueOrderByStartAt(AppointmentStatus status);
}
