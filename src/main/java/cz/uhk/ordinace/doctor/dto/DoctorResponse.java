package cz.uhk.ordinace.doctor.dto;

import cz.uhk.ordinace.doctor.domain.Doctor;

public record DoctorResponse(long id, String firstName, String lastName, boolean active) {

    public static DoctorResponse from(Doctor d) {
        return new DoctorResponse(d.getId(), d.getFirstName(), d.getLastName(), d.isActive());
    }
}
