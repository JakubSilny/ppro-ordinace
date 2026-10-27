package cz.uhk.ordinace.visit.dto;

import cz.uhk.ordinace.visit.domain.ProcedureItem;

public record ProcedureItemResponse(long id, String code, String name, int points, boolean countable) {

    public static ProcedureItemResponse from(ProcedureItem p) {
        return new ProcedureItemResponse(p.getId(), p.getCode(), p.getName(), p.getPoints(), p.isCountable());
    }
}
