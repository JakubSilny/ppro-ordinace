package cz.uhk.ordinace.doctor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cz.uhk.ordinace.common.BusinessRuleException;
import cz.uhk.ordinace.doctor.domain.Doctor;
import cz.uhk.ordinace.doctor.domain.DoctorAbsence;
import cz.uhk.ordinace.doctor.dto.AbsenceRequest;
import cz.uhk.ordinace.doctor.dto.OfficeHoursRequest;
import cz.uhk.ordinace.doctor.repository.DoctorAbsenceRepository;
import cz.uhk.ordinace.doctor.repository.DoctorRepository;
import cz.uhk.ordinace.doctor.repository.OfficeHoursRepository;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneId.of("Europe/Prague"));

    @Mock
    DoctorRepository doctors;
    @Mock
    OfficeHoursRepository officeHours;
    @Mock
    DoctorAbsenceRepository absences;

    DoctorService service;

    @BeforeEach
    void setUp() {
        service = new DoctorService(doctors, officeHours, absences, clock);
        when(doctors.findById(1L)).thenReturn(Optional.of(new Doctor("Marie", "Vyhlídalová")));
    }

    private OfficeHoursRequest hours(int day, String from, String to) {
        return new OfficeHoursRequest(day, LocalTime.parse(from), LocalTime.parse(to), "Na Vyhlídce", true);
    }

    @Test
    void overlappingOfficeHoursOnTheSameDayAreRejected() {
        List<OfficeHoursRequest> req = List.of(hours(1, "08:00", "12:00"), hours(1, "11:00", "15:00"));

        assertThatThrownBy(() -> service.replaceOfficeHours(1, req)).isInstanceOf(BusinessRuleException.class);
        verify(officeHours, never()).deleteByDoctorId(1);
    }

    @Test
    void touchingIntervalsAndDifferentDaysAreAllowed() {
        when(officeHours.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        var saved = service.replaceOfficeHours(1,
                List.of(hours(1, "08:00", "12:00"), hours(1, "12:00", "15:00"), hours(2, "08:00", "12:00")));

        assertThat(saved).hasSize(3);
    }

    @Test
    void officeHoursMustEndAfterTheyStart() {
        assertThatThrownBy(() -> service.replaceOfficeHours(1, List.of(hours(1, "12:00", "12:00"))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void absenceFlagsBookedAppointmentsAndReportsCount() {
        Instant start = Instant.parse("2026-06-10T06:00:00Z");
        Instant end = Instant.parse("2026-06-12T15:00:00Z");
        when(absences.save(any())).thenAnswer(i -> i.getArgument(0));
        when(absences.flagForRebooking(1, start, end)).thenReturn(3);

        AbsenceResult result = service.addAbsence(1, new AbsenceRequest(start, end, "Flu"));

        assertThat(result.flaggedAppointments()).isEqualTo(3);
        DoctorAbsence absence = result.absence();
        assertThat(absence.getReason()).contains("Flu");
    }

    @Test
    void absenceMustEndAfterItStarts() {
        Instant t = Instant.parse("2026-06-10T06:00:00Z");

        assertThatThrownBy(() -> service.addAbsence(1, new AbsenceRequest(t, t, null)))
                .isInstanceOf(BusinessRuleException.class);
        verify(absences, never()).save(any());
    }

    @Test
    void doctorWithUpcomingBookingsCannotBeDeactivated() {
        when(doctors.countUpcomingBookings(1L, clock.instant())).thenReturn(1L);

        assertThatThrownBy(() -> service.deactivate(1)).isInstanceOf(BusinessRuleException.class);
    }
}
