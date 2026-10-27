package cz.uhk.ordinace.visit.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import cz.uhk.ordinace.appointment.service.AppointmentService;
import cz.uhk.ordinace.common.BusinessRuleException;
import cz.uhk.ordinace.common.ConflictException;
import cz.uhk.ordinace.doctor.service.DoctorService;
import cz.uhk.ordinace.visit.domain.ProcedureItem;
import cz.uhk.ordinace.visit.domain.Visit;
import cz.uhk.ordinace.visit.repository.ProcedureItemRepository;
import cz.uhk.ordinace.visit.repository.VisitProcedureRepository;
import cz.uhk.ordinace.visit.repository.VisitRepository;

@ExtendWith(MockitoExtension.class)
class VisitServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-05T08:00:00Z"), ZoneId.of("Europe/Prague"));

    @Mock
    VisitRepository visits;
    @Mock
    VisitProcedureRepository visitProcedures;
    @Mock
    ProcedureItemRepository procedureItems;
    @Mock
    AppointmentService appointments;
    @Mock
    DoctorService doctors;

    private VisitService service() {
        return new VisitService(visits, visitProcedures, procedureItems, appointments, doctors, clock);
    }

    private Visit visit(long id) {
        Visit v = new Visit(7, Optional.empty(), clock.instant());
        ReflectionTestUtils.setField(v, "id", id);
        return v;
    }

    @Test
    void secondVisitForSameAppointmentIsConflict() {
        when(visits.findByAppointmentId(7)).thenReturn(Optional.of(visit(1)));

        assertThatThrownBy(() -> service().open(7, null)).isInstanceOf(ConflictException.class);
        verify(appointments, never()).complete(any(Long.class));
    }

    @Test
    void nonCountableProcedureRejectsQuantity() {
        when(visits.findById(1L)).thenReturn(Optional.of(visit(1)));
        when(procedureItems.findById(2L)).thenReturn(Optional.of(new ProcedureItem("09513", "EKG", 150, false)));

        assertThatThrownBy(() -> service().addProcedure(1, 2, 3)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void sameProcedureTwiceIsConflict() {
        when(visits.findById(1L)).thenReturn(Optional.of(visit(1)));
        when(procedureItems.findById(3L)).thenReturn(Optional.of(new ProcedureItem("09215", "Očkování", 50, true)));
        when(visitProcedures.existsByVisitIdAndProcedureIdAndVoidedAtIsNull(1, 3)).thenReturn(true);

        assertThatThrownBy(() -> service().addProcedure(1, 3, 2)).isInstanceOf(ConflictException.class);
    }

    @Test
    void countableProcedureFreezesPoints() {
        when(visits.findById(1L)).thenReturn(Optional.of(visit(1)));
        when(procedureItems.findById(3L)).thenReturn(Optional.of(new ProcedureItem("09215", "Očkování", 50, true)));
        when(visitProcedures.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        service().addProcedure(1, 3, 3);

        verify(visitProcedures).saveAndFlush(argThat(vp -> vp.getQuantity() == 3 && vp.getPointsAtTime() == 50));
    }
}
