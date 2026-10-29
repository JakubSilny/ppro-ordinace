package cz.uhk.ordinace.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code insuranceId} a {@code insurerId} jsou nepovinné (cizinec, samoplátce). */
public record PatientRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull Integer birthYear,
        @NotBlank @Size(max = 20) String phone,
        @Size(max = 20) String insuranceId,
        Long insurerId) {
}
