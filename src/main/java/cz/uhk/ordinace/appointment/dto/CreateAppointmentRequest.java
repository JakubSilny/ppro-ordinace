package cz.uhk.ordinace.appointment.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code durationMinutes} je nepovinná (výchozí 15 min), násobek 5. */
public record CreateAppointmentRequest(
        @NotNull Long doctorId,
        @NotNull Long patientId,
        @NotNull Instant startAt,
        Integer durationMinutes,
        @Size(max = 200) String reason) {
}
