package cz.uhk.ordinace.visit.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cz.uhk.ordinace.appointment.domain.AppointmentStatus;
import cz.uhk.ordinace.visit.domain.VisitProcedure;
import cz.uhk.ordinace.visit.dto.ReportLine;

public interface VisitProcedureRepository extends JpaRepository<VisitProcedure, Long> {

    List<VisitProcedure> findByVisitIdOrderById(long visitId);

    List<VisitProcedure> findByVisitIdInOrderById(Collection<Long> visitIds);

    boolean existsByVisitIdAndProcedureIdAndVoidedAtIsNull(long visitId, long procedureId);

    @Query("""
            select new cz.uhk.ordinace.visit.dto.ReportLine(a.startAt, p.firstName, p.lastName, p.insuranceId,
                i.code, pi.code, pi.name, vp.quantity, vp.pointsAtTime)
            from VisitProcedure vp
                join vp.procedure pi
                join Visit v on v.id = vp.visitId
                join Appointment a on a.id = v.appointmentId
                join Patient p on p.id = a.patientId
                left join p.insurer i
            where vp.voidedAt is null and a.status = :status and a.doctorId = :doctorId
                and a.startAt >= :from and a.startAt < :to
            order by a.startAt, pi.code
            """)
    List<ReportLine> reportLines(@Param("doctorId") long doctorId, @Param("status") AppointmentStatus status,
            @Param("from") Instant from, @Param("to") Instant to);
}
