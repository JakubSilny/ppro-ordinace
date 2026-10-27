package cz.uhk.ordinace.appointment.dto;

import java.time.Instant;

import cz.uhk.ordinace.appointment.domain.Appointment;
import cz.uhk.ordinace.appointment.domain.AppointmentStatus;

public record AppointmentResponse(long id, long doctorId, long patientId, Instant startAt, Instant endAt,
        AppointmentStatus status, boolean needsRebooking, String reason, Long rescheduledFromId) {

    public static AppointmentResponse from(Appointment a) {
        return new AppointmentResponse(a.getId(), a.getDoctorId(), a.getPatientId(), a.getStartAt(), a.getEndAt(),
                a.getStatus(), a.isNeedsRebooking(), a.getReason().orElse(null),
                a.getRescheduledFromId().orElse(null));
    }
}
