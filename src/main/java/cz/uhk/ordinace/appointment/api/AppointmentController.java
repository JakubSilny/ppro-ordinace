package cz.uhk.ordinace.appointment.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import cz.uhk.ordinace.appointment.domain.Appointment;
import cz.uhk.ordinace.appointment.dto.AppointmentResponse;
import cz.uhk.ordinace.appointment.dto.CreateAppointmentRequest;
import cz.uhk.ordinace.appointment.dto.RescheduleRequest;
import cz.uhk.ordinace.appointment.repository.CancellationStat;
import cz.uhk.ordinace.appointment.service.AppointmentService;
import cz.uhk.ordinace.appointment.service.TimeRange;
import cz.uhk.ordinace.patient.domain.Patient;
import cz.uhk.ordinace.patient.service.PatientService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointments;
    private final PatientService patients;

    public AppointmentController(AppointmentService appointments, PatientService patients) {
        this.appointments = appointments;
        this.patients = patients;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AppointmentResponse create(@Valid @RequestBody CreateAppointmentRequest request) {
        return response(appointments.create(request));
    }

    @GetMapping("/{id}")
    AppointmentResponse get(@PathVariable long id) {
        return response(appointments.get(id));
    }

    @GetMapping
    List<AppointmentResponse> diary(@RequestParam long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return responses(appointments.diary(doctorId, from, to));
    }

    @GetMapping("/free-slots")
    List<TimeRange> freeSlots(@RequestParam long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekOf,
            @RequestParam(defaultValue = "15") int minutes) {
        return appointments.freeSlots(doctorId, weekOf, minutes);
    }

    @GetMapping("/by-patient/{patientId}")
    List<AppointmentResponse> patientHistory(@PathVariable long patientId) {
        return responses(appointments.patientHistory(patientId));
    }

    @GetMapping("/needs-rebooking")
    List<AppointmentResponse> needsRebooking() {
        return responses(appointments.needingRebooking());
    }

    @GetMapping("/cancellation-stats")
    List<CancellationStat> cancellationStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return appointments.cancellationStats(from, to);
    }

    @PostMapping("/{id}/cancel")
    AppointmentResponse cancel(@PathVariable long id) {
        return response(appointments.cancel(id));
    }

    @PostMapping("/{id}/no-show")
    AppointmentResponse noShow(@PathVariable long id) {
        return response(appointments.markNoShow(id));
    }

    @PostMapping("/{id}/reschedule")
    @ResponseStatus(HttpStatus.CREATED)
    AppointmentResponse reschedule(@PathVariable long id, @Valid @RequestBody RescheduleRequest request) {
        return response(appointments.reschedule(id, request));
    }

    private AppointmentResponse response(Appointment a) {
        return AppointmentResponse.from(a, patients.get(a.getPatientId()));
    }

    private List<AppointmentResponse> responses(List<Appointment> list) {
        Map<Long, Patient> byId = patients.getAll(list.stream().map(Appointment::getPatientId).toList());
        return list.stream().map(a -> AppointmentResponse.from(a, byId.get(a.getPatientId()))).toList();
    }
}
