package cz.uhk.ordinace.doctor.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AbsenceRequest(@NotNull Instant startAt, @NotNull Instant endAt, @Size(max = 200) String reason) {
}
