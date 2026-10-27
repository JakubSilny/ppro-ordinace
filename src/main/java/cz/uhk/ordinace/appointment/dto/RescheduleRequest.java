package cz.uhk.ordinace.appointment.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

/** Bez {@code durationMinutes} se zachová délka původní rezervace. */
public record RescheduleRequest(@NotNull Instant startAt, Integer durationMinutes) {
}
