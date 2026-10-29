package cz.uhk.ordinace.doctor.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Weekday 1 = pondělí … 7 = neděle; nebookovatelný interval (jiná pobočka) se v diáři jen zobrazí. */
public record OfficeHoursRequest(
        @NotNull @Min(1) @Max(7) Integer weekday,
        @NotNull LocalTime from,
        @NotNull LocalTime to,
        @NotBlank @Size(max = 100) String location,
        boolean bookable) {
}
