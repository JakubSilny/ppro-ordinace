package cz.uhk.ordinace.appointment.dto;

public record CancellationStat(long patientId, String firstName, String lastName, String phone, long cancelled,
        long noShow) {
}
