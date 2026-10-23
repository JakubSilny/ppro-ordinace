package cz.uhk.ordinace.patient.dto;

import cz.uhk.ordinace.insurer.dto.InsurerResponse;
import cz.uhk.ordinace.patient.domain.Patient;

public record PatientResponse(long id, String firstName, String lastName, int birthYear, String phone,
        String insuranceId, InsurerResponse insurer, boolean active) {

    public static PatientResponse from(Patient p) {
        return new PatientResponse(p.getId(), p.getFirstName(), p.getLastName(), p.getBirthYear(), p.getPhone(),
                p.getInsuranceId().orElse(null), p.getInsurer().map(InsurerResponse::from).orElse(null), p.isActive());
    }
}
