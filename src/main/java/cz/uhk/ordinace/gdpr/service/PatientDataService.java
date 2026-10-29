package cz.uhk.ordinace.gdpr.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cz.uhk.ordinace.appointment.domain.Appointment;
import cz.uhk.ordinace.appointment.service.AppointmentService;
import cz.uhk.ordinace.audit.domain.AuditEntry;
import cz.uhk.ordinace.audit.service.AuditService;
import cz.uhk.ordinace.patient.domain.Patient;
import cz.uhk.ordinace.patient.service.PatientService;
import cz.uhk.ordinace.visit.service.VisitDetail;
import cz.uhk.ordinace.visit.service.VisitService;

/** Podklady pro žádost subjektu údajů (čl. 15 a 20 GDPR): vše, co o pacientovi evidujeme, a kdo to četl. */
@Service
public class PatientDataService {

    public record AppointmentWithVisit(Appointment appointment, Optional<VisitDetail> visit) {
    }

    public record PatientData(Patient patient, List<AppointmentWithVisit> appointments) {
    }

    private final PatientService patients;
    private final AppointmentService appointments;
    private final VisitService visits;
    private final AuditService audit;

    public PatientDataService(PatientService patients, AppointmentService appointments, VisitService visits,
            AuditService audit) {
        this.patients = patients;
        this.appointments = appointments;
        this.visits = visits;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PatientData export(long patientId) {
        Patient patient = patients.get(patientId);
        List<Appointment> history = appointments.patientHistory(patientId);
        Map<Long, VisitDetail> visitsByAppointment = visits.findByAppointments(ids(history));
        return new PatientData(patient, history.stream()
                .map(a -> new AppointmentWithVisit(a, Optional.ofNullable(visitsByAppointment.get(a.getId()))))
                .toList());
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> accessLog(long patientId) {
        List<Long> appointmentIds = ids(appointments.patientHistory(patientId));
        List<Long> visitIds = visits.findByAppointments(appointmentIds).values().stream()
                .map(v -> v.visit().getId()).toList();
        return audit.forPatient(patientId, appointmentIds, visitIds);
    }

    private static List<Long> ids(List<Appointment> appointments) {
        return appointments.stream().map(Appointment::getId).toList();
    }
}
