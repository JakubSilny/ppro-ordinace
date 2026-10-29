package cz.uhk.ordinace.appointment.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cz.uhk.ordinace.appointment.domain.Appointment;
import cz.uhk.ordinace.appointment.domain.AppointmentStatus;
import cz.uhk.ordinace.appointment.dto.CreateAppointmentRequest;
import cz.uhk.ordinace.appointment.dto.RescheduleRequest;
import cz.uhk.ordinace.appointment.repository.AppointmentRepository;
import cz.uhk.ordinace.appointment.dto.CancellationStat;
import cz.uhk.ordinace.common.BusinessRuleException;
import cz.uhk.ordinace.common.ConflictException;
import cz.uhk.ordinace.common.NotFoundException;
import cz.uhk.ordinace.doctor.domain.OfficeHours;
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

    @Transactional(readOnly = true)
    public List<Appointment> patientHistory(long patientId) {
        patients.get(patientId);
        return appointments.findByPatientIdOrderByStartAtDesc(patientId);
    }

    /** Kdo ruší nebo nechodí v období {@code from}–{@code to} včetně, nejčastější první. */
    @Transactional(readOnly = true)
    public List<CancellationStat> cancellationStats(LocalDate from, LocalDate to) {
        ZoneId zone = clock.getZone();
        return appointments.cancellationStats(List.of(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW),
                from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant());
    }

    /** Volná okna lékaře v týdnu obsahujícím {@code weekOf}, dlouhá aspoň {@code minutes}; minulost se nenabízí. */
    @Transactional(readOnly = true)
    public List<TimeRange> freeSlots(long doctorId, LocalDate weekOf, int minutes) {
        requireValidDuration(minutes);
        ZoneId zone = clock.getZone();
        LocalDate monday = weekOf.with(DayOfWeek.MONDAY);
        Instant from = monday.atStartOfDay(zone).toInstant();
        Instant to = monday.plusWeeks(1).atStartOfDay(zone).toInstant();

        List<TimeRange> open = doctors.getOfficeHours(doctorId).stream().filter(OfficeHours::isBookable)
                .map(h -> {
                    LocalDate day = monday.plusDays(h.getWeekday() - 1L);
                    return new TimeRange(day.atTime(h.getFromTime()).atZone(zone).toInstant(),
                            day.atTime(h.getToTime()).atZone(zone).toInstant());
                }).toList();
        List<TimeRange> busy = new ArrayList<>();
        appointments.findDiary(doctorId, AppointmentStatus.OCCUPYING, from, to)
                .forEach(a -> busy.add(new TimeRange(a.getStartAt(), a.getEndAt())));
        doctors.absencesBetween(doctorId, from, to)
                .forEach(a -> busy.add(new TimeRange(a.getStartAt(), a.getEndAt())));
        busy.add(new TimeRange(from, roundUpToFiveMinutes(clock.instant())));
        return TimeRange.free(open, busy, Duration.ofMinutes(minutes));
    }

    @Transactional
    public Appointment complete(long id) {
        Appointment appointment = get(id);
        appointment.complete(clock.instant());
        return appointments.saveAndFlush(appointment);
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
        doctors.lock(old.getDoctorId()); // zámek lékaře dřív než řádku rezervace, stejně jako při zadání absence
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
        // zámky lékaře a pacienta: absence ani vyřazení se mezi kontrolou a uložením nevklíní
        if (!doctors.lock(doctorId).isActive() || !patients.lock(patientId).isActive()) {
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

    private static void requireValidDuration(int minutes) {
        if (minutes < 5 || minutes % 5 != 0) {
            throw new BusinessRuleException("Duration must be a positive multiple of 5 minutes");
        }
    }

    private static Instant roundUpToFiveMinutes(Instant t) {
        long step = 300;
        return Instant.ofEpochSecond(Math.ceilDiv(t.getEpochSecond(), step) * step);
    }

    /** Pravidla, která databáze neumí: délka, dnešek, jeden bookable interval, absence. */
    private void checkSlot(long doctorId, Instant start, Instant end, int minutes) {
        requireValidDuration(minutes);
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
