package cz.uhk.ordinace.gdpr.dto;

import java.time.Instant;
import java.util.List;

import cz.uhk.ordinace.appointment.domain.AppointmentStatus;
import cz.uhk.ordinace.gdpr.service.PatientDataService.PatientData;
import cz.uhk.ordinace.patient.dto.PatientResponse;
import cz.uhk.ordinace.visit.dto.VisitResponse;

public record PatientDataResponse(Instant exportedAt, PatientResponse patient, List<AppointmentEntry> appointments) {

    public record AppointmentEntry(long id, long doctorId, Instant startAt, Instant endAt, AppointmentStatus status,
            String reason, VisitResponse visit) {
    }

    public static PatientDataResponse from(PatientData data, Instant now) {
        return new PatientDataResponse(now, PatientResponse.from(data.patient()),
                data.appointments().stream().map(e -> new AppointmentEntry(e.appointment().getId(),
                        e.appointment().getDoctorId(), e.appointment().getStartAt(), e.appointment().getEndAt(),
                        e.appointment().getStatus(), e.appointment().getReason().orElse(null),
                        e.visit().map(VisitResponse::from).orElse(null))).toList());
    }
}
