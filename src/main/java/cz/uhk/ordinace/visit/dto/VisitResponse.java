package cz.uhk.ordinace.visit.dto;

import java.time.Instant;
import java.util.List;

import cz.uhk.ordinace.visit.domain.VisitProcedure;
import cz.uhk.ordinace.visit.service.VisitDetail;

public record VisitResponse(long id, long appointmentId, String note, Instant createdAt, List<Item> procedures,
        long totalPoints) {

    public record Item(long id, long procedureId, String code, String name, int quantity, int pointsAtTime,
            Instant voidedAt) {

        static Item from(VisitProcedure vp) {
            return new Item(vp.getId(), vp.getProcedure().getId(), vp.getProcedure().getCode(),
                    vp.getProcedure().getName(), vp.getQuantity(), vp.getPointsAtTime(),
                    vp.getVoidedAt().orElse(null));
        }
    }

    public static VisitResponse from(VisitDetail d) {
        long total = d.procedures().stream().filter(p -> p.getVoidedAt().isEmpty())
                .mapToLong(p -> (long) p.getQuantity() * p.getPointsAtTime()).sum();
        return new VisitResponse(d.visit().getId(), d.visit().getAppointmentId(), d.visit().getNote().orElse(null),
                d.visit().getCreatedAt(), d.procedures().stream().map(Item::from).toList(), total);
    }
}
