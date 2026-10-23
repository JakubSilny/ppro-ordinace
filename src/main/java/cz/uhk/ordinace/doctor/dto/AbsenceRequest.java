package cz.uhk.ordinace.doctor.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public record AbsenceRequest(@NotNull Instant startAt, @NotNull Instant endAt, String reason) {
}
