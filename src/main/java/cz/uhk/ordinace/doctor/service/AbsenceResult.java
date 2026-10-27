package cz.uhk.ordinace.doctor.service;

import cz.uhk.ordinace.doctor.domain.DoctorAbsence;

public record AbsenceResult(DoctorAbsence absence, int flaggedAppointments) {
}
