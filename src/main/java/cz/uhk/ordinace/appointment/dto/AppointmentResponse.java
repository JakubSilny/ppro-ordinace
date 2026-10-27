package cz.uhk.ordinace.appointment.dto;

import java.time.Instant;

import cz.uhk.ordinace.appointment.domain.Appointment;
import cz.uhk.ordinace.appointment.domain.AppointmentStatus;
import cz.uhk.ordinace.patient.domain.Patient;

/** Rezervace se jménem a telefonem pacienta, aby recepce nemusela dohledávat. */
public record AppointmentResponse(long id, long doctorId, long patientId, String patientName, String patientPhone,
        Instant startAt, Instant endAt, AppointmentStatus status, boolean needsRebooking, String reason,
        Long rescheduledFromId) {

    public static AppointmentResponse from(Appointment a, Patient p) {
        return new AppointmentResponse(a.getId(), a.getDoctorId(), a.getPatientId(),
                p.getLastName() + " " + p.getFirstName(), p.getPhone(), a.getStartAt(), a.getEndAt(), a.getStatus(),
                a.isNeedsRebooking(), a.getReason().orElse(null), a.getRescheduledFromId().orElse(null));
    }
}
