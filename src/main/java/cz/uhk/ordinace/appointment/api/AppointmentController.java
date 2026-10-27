package cz.uhk.ordinace.appointment.api;

import java.time.LocalDate;
import java.util.List;

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

import cz.uhk.ordinace.appointment.dto.AppointmentResponse;
import cz.uhk.ordinace.appointment.dto.CreateAppointmentRequest;
import cz.uhk.ordinace.appointment.dto.RescheduleRequest;
import cz.uhk.ordinace.appointment.service.AppointmentService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointments;

    public AppointmentController(AppointmentService appointments) {
        this.appointments = appointments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AppointmentResponse create(@Valid @RequestBody CreateAppointmentRequest request) {
        return AppointmentResponse.from(appointments.create(request));
    }

    @GetMapping("/{id}")
    AppointmentResponse get(@PathVariable long id) {
        return AppointmentResponse.from(appointments.get(id));
    }

    @GetMapping
    List<AppointmentResponse> diary(@RequestParam long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return appointments.diary(doctorId, from, to).stream().map(AppointmentResponse::from).toList();
    }

    @GetMapping("/needs-rebooking")
    List<AppointmentResponse> needsRebooking() {
        return appointments.needingRebooking().stream().map(AppointmentResponse::from).toList();
    }

    @PostMapping("/{id}/cancel")
    AppointmentResponse cancel(@PathVariable long id) {
        return AppointmentResponse.from(appointments.cancel(id));
    }

    @PostMapping("/{id}/no-show")
    AppointmentResponse noShow(@PathVariable long id) {
        return AppointmentResponse.from(appointments.markNoShow(id));
    }

    @PostMapping("/{id}/reschedule")
    @ResponseStatus(HttpStatus.CREATED)
    AppointmentResponse reschedule(@PathVariable long id, @Valid @RequestBody RescheduleRequest request) {
        return AppointmentResponse.from(appointments.reschedule(id, request));
    }
}
