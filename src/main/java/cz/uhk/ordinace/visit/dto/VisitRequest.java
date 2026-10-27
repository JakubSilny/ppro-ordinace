package cz.uhk.ordinace.visit.dto;

import jakarta.validation.constraints.Size;

public record VisitRequest(@Size(max = 500) String note) {
}
