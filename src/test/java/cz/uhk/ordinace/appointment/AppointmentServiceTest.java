package cz.uhk.ordinace.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import cz.uhk.ordinace.appointment.domain.Appointment;
import cz.uhk.ordinace.appointment.domain.AppointmentStatus;
import cz.uhk.ordinace.appointment.dto.CreateAppointmentRequest;
import cz.uhk.ordinace.appointment.dto.RescheduleRequest;
import cz.uhk.ordinace.appointment.repository.AppointmentRepository;
import cz.uhk.ordinace.common.BusinessRuleException;
import cz.uhk.ordinace.common.ConflictException;
import cz.uhk.ordinace.doctor.domain.Doctor;
import cz.uhk.ordinace.doctor.domain.OfficeHours;
import cz.uhk.ordinace.doctor.service.DoctorService;
import cz.uhk.ordinace.patient.domain.Patient;
import cz.uhk.ordinace.patient.service.PatientService;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");

    // čtvrtek 1. 10. 2026, poledne; pondělí 5. 10. je první pracovní den v testu
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), PRAGUE);

    @Mock
    AppointmentRepository appointments;
    @Mock
    DoctorService doctors;
    @Mock
    PatientService patients;

    AppointmentService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(appointments, doctors, patients, clock);
        lenient().when(doctors.get(1L)).thenReturn(new Doctor("Marie", "Vyhlídalová"));
        lenient().when(patients.get(2L)).thenReturn(
                new Patient("Jan", "Novák", 1980, "602111222", Optional.empty(), Optional.empty()));
        lenient().when(doctors.getOfficeHours(1L)).thenReturn(List.of(
                hours(1, "08:00", "12:00", true), hours(1, "13:00", "16:00", true), hours(2, "08:00", "12:00", false)));
        lenient().when(appointments.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    private OfficeHours hours(int day, String from, String to, boolean bookable) {
        return new OfficeHours(1, day, LocalTime.parse(from), LocalTime.parse(to), "Na Vyhlídce", bookable);
    }

    private Instant at(String date, String time) {
        return ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), PRAGUE).toInstant();
    }

    private CreateAppointmentRequest request(Instant start, Integer minutes) {
        return new CreateAppointmentRequest(1L, 2L, start, minutes, null);
    }

    @Test
    void defaultDurationIsFifteenMinutes() {
        Appointment a = service.create(request(at("2026-10-05", "08:00"), null));

        assertThat(a.getEndAt()).isEqualTo(at("2026-10-05", "08:15"));
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void appointmentMayEndExactlyWhenIntervalEnds() {
        assertThat(service.create(request(at("2026-10-05", "11:30"), 30))).isNotNull();
    }

    @Test
    void appointmentCannotCrossLunchBreak() {
        assertThatThrownBy(() -> service.create(request(at("2026-10-05", "11:50"), 30)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void nonBookableIntervalIsRejected() {
        assertThatThrownBy(() -> service.create(request(at("2026-10-06", "09:00"), 15)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void daysWithoutOfficeHoursAreRejected() {
        assertThatThrownBy(() -> service.create(request(at("2026-10-07", "09:00"), 15)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void durationMustBeMultipleOfFive() {
        assertThatThrownBy(() -> service.create(request(at("2026-10-05", "08:00"), 12)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void pastDaysAreRejectedButEarlierTodayIsAllowed() {
        assertThatThrownBy(() -> service.create(request(at("2026-09-28", "08:00"), 15)))
                .isInstanceOf(BusinessRuleException.class);

        // dnes (čtvrtek) není ordinace, ale pravidlo "od začátku dne" se kontroluje dřív než hodiny
        assertThatThrownBy(() -> service.create(request(at("2026-10-01", "08:00"), 15)))
                .hasMessageContaining("office-hours");
    }

    @Test
    void absenceBlocksBooking() {
        Instant start = at("2026-10-05", "08:00");
        when(doctors.hasAbsence(1L, start, start.plusSeconds(900))).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(start, 15))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void overlappingDoctorAppointmentIsConflict() {
        when(appointments.existsByDoctorIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(any(Long.class), any(),
                any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(at("2026-10-05", "08:00"), 15)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void sameLocalTimeIsAcceptedBeforeAndAfterDaylightSavingChange() {
        // změna času 25. 10. 2026: pondělí 08:00 je 06:00 UTC před ní a 07:00 UTC po ní
        assertThat(service.create(request(at("2026-10-19", "08:00"), 15)).getStartAt())
                .isEqualTo(Instant.parse("2026-10-19T06:00:00Z"));
        assertThat(service.create(request(at("2026-10-26", "08:00"), 15)).getStartAt())
                .isEqualTo(Instant.parse("2026-10-26T07:00:00Z"));
    }

    @Test
    void cancelledAppointmentCannotBeCancelledAgain() {
        Appointment a = booked(10, "2026-10-05", "08:00");
        when(appointments.findById(10L)).thenReturn(Optional.of(a));
        when(appointments.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service.cancel(10).getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThatThrownBy(() -> service.cancel(10)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void noShowOnlyAfterStart() {
        Appointment future = booked(11, "2026-10-05", "08:00");
        Appointment past = booked(12, "2026-09-30", "08:00");
        when(appointments.findById(11L)).thenReturn(Optional.of(future));
        when(appointments.findById(12L)).thenReturn(Optional.of(past));
        when(appointments.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> service.markNoShow(11)).isInstanceOf(BusinessRuleException.class);
        assertThat(service.markNoShow(12).getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
    }

    @Test
    void rescheduleMarksOldAndLinksNew() {
        Appointment old = booked(13, "2026-10-05", "08:00");
        when(appointments.findById(13L)).thenReturn(Optional.of(old));

        Appointment moved = service.reschedule(13, new RescheduleRequest(at("2026-10-12", "09:00"), null));

        assertThat(old.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULED);
        assertThat(moved.getRescheduledFromId()).contains(13L);
        assertThat(moved.getStartAt()).isEqualTo(at("2026-10-12", "09:00"));
        assertThat(moved.getEndAt()).isEqualTo(at("2026-10-12", "09:15"));
    }

    private Appointment booked(long id, String date, String time) {
        Instant start = at(date, time);
        Appointment a = new Appointment(1, 2, start, start.plusSeconds(900), Optional.empty(), Optional.empty());
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }
}
