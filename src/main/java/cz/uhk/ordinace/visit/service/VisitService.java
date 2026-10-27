package cz.uhk.ordinace.visit.service;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cz.uhk.ordinace.appointment.domain.AppointmentStatus;
import cz.uhk.ordinace.appointment.service.AppointmentService;
import cz.uhk.ordinace.common.ConflictException;
import cz.uhk.ordinace.common.NotFoundException;
import cz.uhk.ordinace.doctor.service.DoctorService;
import cz.uhk.ordinace.visit.domain.ProcedureItem;
import cz.uhk.ordinace.visit.domain.Visit;
import cz.uhk.ordinace.visit.domain.VisitProcedure;
import cz.uhk.ordinace.visit.repository.ProcedureItemRepository;
import cz.uhk.ordinace.visit.repository.VisitProcedureRepository;
import cz.uhk.ordinace.visit.repository.VisitRepository;

@Service
public class VisitService {

    private final VisitRepository visits;
    private final VisitProcedureRepository visitProcedures;
    private final ProcedureItemRepository procedureItems;
    private final AppointmentService appointments;
    private final DoctorService doctors;
    private final Clock clock;

    public VisitService(VisitRepository visits, VisitProcedureRepository visitProcedures,
            ProcedureItemRepository procedureItems, AppointmentService appointments, DoctorService doctors,
            Clock clock) {
        this.visits = visits;
        this.visitProcedures = visitProcedures;
        this.procedureItems = procedureItems;
        this.appointments = appointments;
        this.doctors = doctors;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProcedureItem> catalog() {
        return procedureItems.findAllByOrderByCode();
    }

    /** Zapíše návštěvu k rezervaci, která už začala; rezervace přejde do COMPLETED. */
    @Transactional
    public VisitDetail open(long appointmentId, String note) {
        if (visits.findByAppointmentId(appointmentId).isPresent()) {
            throw new ConflictException("Visit is already recorded for this appointment");
        }
        appointments.complete(appointmentId);
        Visit visit = visits.saveAndFlush(new Visit(appointmentId, trimmed(note), clock.instant()));
        return new VisitDetail(visit, List.of());
    }

    @Transactional(readOnly = true)
    public VisitDetail byAppointment(long appointmentId) {
        Visit visit = visits.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new NotFoundException("No visit for appointment " + appointmentId));
        return detail(visit);
    }

    @Transactional(readOnly = true)
    public Optional<VisitDetail> findByAppointment(long appointmentId) {
        return visits.findByAppointmentId(appointmentId).map(this::detail);
    }

    @Transactional
    public VisitDetail updateNote(long visitId, String note) {
        Visit visit = get(visitId);
        visit.updateNote(trimmed(note));
        return detail(visits.save(visit));
    }

    @Transactional
    public VisitDetail addProcedure(long visitId, long procedureId, int quantity) {
        Visit visit = get(visitId);
        ProcedureItem item = procedureItems.findById(procedureId)
                .orElseThrow(() -> new NotFoundException("Procedure " + procedureId + " not found"));
        if (visitProcedures.existsByVisitIdAndProcedureIdAndVoidedAtIsNull(visitId, procedureId)) {
            throw new ConflictException("Procedure " + item.getCode() + " is already recorded on this visit");
        }
        visitProcedures.saveAndFlush(new VisitProcedure(visitId, item, quantity));
        return detail(visit);
    }

    @Transactional
    public VisitDetail voidProcedure(long visitId, long visitProcedureId) {
        Visit visit = get(visitId);
        VisitProcedure vp = visitProcedures.findById(visitProcedureId).filter(p -> p.getVisitId() == visitId)
                .orElseThrow(() -> new NotFoundException("Procedure record " + visitProcedureId + " not found"));
        vp.voidAt(clock.instant());
        visitProcedures.save(vp);
        return detail(visit);
    }

    @Transactional(readOnly = true)
    public MonthlyReport monthlyReport(long doctorId, YearMonth month) {
        doctors.get(doctorId);
        ZoneId zone = clock.getZone();
        return MonthlyReport.of(doctorId, month, visitProcedures.reportLines(doctorId, AppointmentStatus.COMPLETED,
                month.atDay(1).atStartOfDay(zone).toInstant(),
                month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()));
    }

    private Visit get(long visitId) {
        return visits.findById(visitId).orElseThrow(() -> new NotFoundException("Visit " + visitId + " not found"));
    }

    private VisitDetail detail(Visit visit) {
        return new VisitDetail(visit, visitProcedures.findByVisitIdOrderById(visit.getId()));
    }

    private static Optional<String> trimmed(String s) {
        return Optional.ofNullable(s).map(String::trim).filter(t -> !t.isEmpty());
    }
}
