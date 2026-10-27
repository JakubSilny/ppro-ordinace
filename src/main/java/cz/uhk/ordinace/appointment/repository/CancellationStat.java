package cz.uhk.ordinace.appointment.repository;

public record CancellationStat(long patientId, String firstName, String lastName, String phone, long cancelled,
        long noShow) {
}
