package cz.uhk.ordinace.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code insuranceId} a {@code insurerId} jsou nepovinné (cizinec, samoplátce). */
public record PatientRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull Integer birthYear,
        @NotBlank String phone,
        String insuranceId,
        Long insurerId) {
}
