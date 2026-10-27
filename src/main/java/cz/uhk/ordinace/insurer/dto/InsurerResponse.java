package cz.uhk.ordinace.insurer.dto;

import cz.uhk.ordinace.insurer.domain.Insurer;

public record InsurerResponse(long id, String code, String name) {

    public static InsurerResponse from(Insurer insurer) {
        return new InsurerResponse(insurer.getId(), insurer.getCode(), insurer.getName());
    }
}
