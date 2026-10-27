package cz.uhk.ordinace.visit.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Bez {@code quantity} se zapíše jeden výkon. */
public record AddProcedureRequest(@NotNull Long procedureId, @Min(1) Integer quantity) {
}
