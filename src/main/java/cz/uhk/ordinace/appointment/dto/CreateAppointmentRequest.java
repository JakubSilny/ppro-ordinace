package cz.uhk.ordinace.appointment.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

/** {@code durationMinutes} je nepovinná (výchozí 15 min), násobek 5. */
public record CreateAppointmentRequest(
        @NotNull Long doctorId,
        @NotNull Long patientId,
        @NotNull Instant startAt,
        Integer durationMinutes,
        String reason) {
}
