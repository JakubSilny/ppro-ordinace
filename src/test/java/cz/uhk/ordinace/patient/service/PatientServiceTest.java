package cz.uhk.ordinace.patient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import cz.uhk.ordinace.common.BusinessRuleException;
import cz.uhk.ordinace.common.ConflictException;
import cz.uhk.ordinace.insurer.service.InsurerService;
import cz.uhk.ordinace.patient.domain.Patient;
import cz.uhk.ordinace.patient.dto.PatientRequest;
import cz.uhk.ordinace.patient.repository.PatientRepository;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneId.of("Europe/Prague"));

    @Mock
    PatientRepository repository;
    @Mock
    InsurerService insurers;

    private PatientService service() {
        return new PatientService(repository, insurers, clock);
    }

    private Patient existing(long id) {
        Patient p = new Patient("Jan", "Novák", 1980, "602111222", Optional.empty(), Optional.empty());
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private PatientRequest request(String insuranceId) {
        return new PatientRequest("Jan", "Novák", 1980, "+420 602 111 222", insuranceId, null);
    }

    @Test
    void duplicateInsuranceIdIsConflictWithExistingId() {
        when(repository.findByInsuranceId("123")).thenReturn(Optional.of(existing(5)));

        assertThatThrownBy(() -> service().create(request("123"), false))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getExistingId()).hasValue(5));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void sameIdentityWithoutInsuranceIdNeedsConfirmation() {
        when(repository.findSameIdentity("Jan", "Novák", 1980, "602111222")).thenReturn(List.of(existing(7)));

        assertThatThrownBy(() -> service().create(request(" "), false))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getExistingId()).hasValue(7));
    }

    @Test
    void confirmedDuplicateIsSavedWithNormalizedPhone() {
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        Patient saved = service().create(request(null), true);

        assertThat(saved.getPhone()).isEqualTo("602111222");
        assertThat(saved.getInsuranceId()).isEmpty();
    }

    @Test
    void birthYearInTheFutureIsRejected() {
        PatientRequest req = new PatientRequest("Jan", "Novák", 2027, "602111222", null, null);

        assertThatThrownBy(() -> service().create(req, true)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void patientWithUpcomingBookingsCannotBeDeactivated() {
        Patient p = existing(3);
        when(repository.findById(3L)).thenReturn(Optional.of(p));
        when(repository.countUpcomingBookings(3L, clock.instant())).thenReturn(2L);

        assertThatThrownBy(() -> service().deactivate(3)).isInstanceOf(BusinessRuleException.class);
        assertThat(p.isActive()).isTrue();
    }
}
