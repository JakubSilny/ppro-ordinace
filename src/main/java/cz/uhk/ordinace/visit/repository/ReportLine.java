package cz.uhk.ordinace.visit.repository;

import java.time.Instant;

/** Jeden nestornovaný výkon dokončené návštěvy; {@code insuranceId} a {@code insurerCode} mohou chybět. */
public record ReportLine(Instant performedAt, String patientFirstName, String patientLastName, String insuranceId,
        String insurerCode, String code, String name, int quantity, int pointsEach) {

    public long points() {
        return (long) quantity * pointsEach;
    }
}
