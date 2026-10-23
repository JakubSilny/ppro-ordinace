package cz.uhk.ordinace.doctor.dto;

import jakarta.validation.constraints.NotBlank;

public record DoctorRequest(@NotBlank String firstName, @NotBlank String lastName) {
}
