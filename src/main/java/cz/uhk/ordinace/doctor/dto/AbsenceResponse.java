package cz.uhk.ordinace.doctor.dto;

import java.time.Instant;

import cz.uhk.ordinace.doctor.domain.DoctorAbsence;

/** {@code flaggedAppointments} je počet rezervací označených k přeobjednání (0 u pouhého výpisu). */
public record AbsenceResponse(long id, long doctorId, Instant startAt, Instant endAt, String reason,
        int flaggedAppointments) {

    public static AbsenceResponse from(DoctorAbsence a, int flagged) {
        return new AbsenceResponse(a.getId(), a.getDoctorId(), a.getStartAt(), a.getEndAt(),
                a.getReason().orElse(null), flagged);
    }
}
