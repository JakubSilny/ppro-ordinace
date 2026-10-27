package cz.uhk.ordinace.appointment.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cz.uhk.ordinace.appointment.domain.Appointment;
import cz.uhk.ordinace.appointment.domain.AppointmentStatus;
import cz.uhk.ordinace.appointment.dto.CreateAppointmentRequest;
import cz.uhk.ordinace.appointment.dto.RescheduleRequest;
import cz.uhk.ordinace.appointment.repository.AppointmentRepository;
import cz.uhk.ordinace.common.BusinessRuleException;
import cz.uhk.ordinace.common.ConflictException;
import cz.uhk.ordinace.common.NotFoundException;
import cz.uhk.ordinace.doctor.service.DoctorService;
import cz.uhk.ordinace.patient.service.PatientService;

@Service
public class AppointmentService {

    static final int DEFAULT_MINUTES = 15;

    private final AppointmentRepository appointments;
    private final DoctorService doctors;
    private final PatientService patients;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointments, DoctorService doctors, PatientService patients,
            Clock clock) {
        this.appointments = appointments;
        this.doctors = doctors;
        this.patients = patients;
        this.clock = clock;
    }

    @Transactional
    public Appointment create(CreateAppointmentRequest req) {
        int minutes = req.durationMinutes() == null ? DEFAULT_MINUTES : req.durationMinutes();
        Optional<String> reason = Optional.ofNullable(req.reason()).map(String::trim).filter(s -> !s.isEmpty());
        return book(req.doctorId(), req.patientId(), req.startAt(), minutes, reason, Optional.empty());
    }

    @Transactional(readOnly = true)
    public Appointment get(long id) {
        return appointments.findById(id).orElseThrow(() -> new NotFoundException("Appointment " + id + " not found"));
    }

    /** Diář lékaře od {@code from} do {@code to} včetně; zrušené a přesunuté se nezobrazují. */
    @Transactional(readOnly = true)
    public List<Appointment> diary(long doctorId, LocalDate from, LocalDate to) {
        doctors.get(doctorId);
        ZoneId zone = clock.getZone();
        return appointments.findDiary(doctorId, AppointmentStatus.OCCUPYING,
                from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant());
    }

    @Transactional(readOnly = true)
    public List<Appointment> needingRebooking() {
        return appointments.findByStatusAndNeedsRebookingTrueOrderByStartAt(AppointmentStatus.BOOKED);
    }

    @Transactional
    public Appointment cancel(long id) {
        Appointment appointment = get(id);
        appointment.cancel();
        return appointments.save(appointment);
    }

    @Transactional
    public Appointment markNoShow(long id) {
        Appointment appointment = get(id);
        appointment.markNoShow(clock.instant());
        return appointments.save(appointment);
    }

    /** Stará rezervace přejde do RESCHEDULED a nová na ni odkazuje; při chybě se vrátí obojí. */
    @Transactional
    public Appointment reschedule(long id, RescheduleRequest req) {
        Appointment old = get(id);
        int minutes = req.durationMinutes() == null
                ? (int) Duration.between(old.getStartAt(), old.getEndAt()).toMinutes()
                : req.durationMinutes();
        old.markRescheduled();
        appointments.saveAndFlush(old);
        return book(old.getDoctorId(), old.getPatientId(), req.startAt(), minutes, old.getReason(),
                Optional.of(old.getId()));
    }

    private Appointment book(long doctorId, long patientId, Instant start, int minutes, Optional<String> reason,
            Optional<Long> rescheduledFrom) {
        if (!doctors.get(doctorId).isActive() || !patients.get(patientId).isActive()) {
            throw new BusinessRuleException("Doctor and patient must be active");
        }
        Instant end = start.plus(Duration.ofMinutes(minutes));
        checkSlot(doctorId, start, end, minutes);

        if (appointments.existsByDoctorIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(doctorId,
                AppointmentStatus.OCCUPYING, end, start)) {
            throw new ConflictException("Doctor already has an appointment in this time");
        }
        if (appointments.existsByPatientIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(patientId,
                AppointmentStatus.OCCUPYING, end, start)) {
            throw new ConflictException("Patient already has an appointment in this time");
        }
        return appointments.saveAndFlush(new Appointment(doctorId, patientId, start, end, reason, rescheduledFrom));
    }

    /** Pravidla, která databáze neumí: délka, dnešek, jeden bookable interval, absence. */
    private void checkSlot(long doctorId, Instant start, Instant end, int minutes) {
        if (minutes < 5 || minutes % 5 != 0) {
            throw new BusinessRuleException("Duration must be a positive multiple of 5 minutes");
        }
        ZoneId zone = clock.getZone();
        if (start.isBefore(LocalDate.now(clock).atStartOfDay(zone).toInstant())) {
            throw new BusinessRuleException("Appointments cannot start before today");
        }
        ZonedDateTime s = start.atZone(zone);
        ZonedDateTime e = end.atZone(zone);
        boolean insideOneInterval = s.toLocalDate().equals(e.toLocalDate())
                && doctors.getOfficeHours(doctorId).stream().anyMatch(h -> h.isBookable()
                        && h.getWeekday() == s.getDayOfWeek().getValue()
                        && !s.toLocalTime().isBefore(h.getFromTime())
                        && !e.toLocalTime().isAfter(h.getToTime()));
        if (!insideOneInterval) {
            throw new BusinessRuleException("Appointment must fit into one bookable office-hours interval");
        }
        if (doctors.hasAbsence(doctorId, start, end)) {
            throw new BusinessRuleException("Doctor is absent in this time");
        }
    }
}
