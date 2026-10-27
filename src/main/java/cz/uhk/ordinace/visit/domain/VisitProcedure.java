package cz.uhk.ordinace.visit.domain;

import java.time.Instant;
import java.util.Optional;

import cz.uhk.ordinace.common.BusinessRuleException;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Vazba M:N návštěva–výkon; body jsou zmražené v době zápisu, storno jen nastaví {@code voidedAt}. */
@Entity
@Table(name = "visit_procedure")
public class VisitProcedure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long visitId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "procedure_id")
    private ProcedureItem procedure;

    private int quantity;

    private int pointsAtTime;

    private Instant voidedAt;

    protected VisitProcedure() {
    }

    public VisitProcedure(long visitId, ProcedureItem procedure, int quantity) {
        if (quantity < 1 || (!procedure.isCountable() && quantity != 1)) {
            throw new BusinessRuleException("Procedure " + procedure.getCode() + " cannot have quantity " + quantity);
        }
        this.visitId = visitId;
        this.procedure = procedure;
        this.quantity = quantity;
        this.pointsAtTime = procedure.getPoints();
    }

    public void voidAt(Instant now) {
        if (voidedAt != null) {
            throw new BusinessRuleException("Procedure is already voided");
        }
        voidedAt = now;
    }

    public Long getId() {
        return id;
    }

    public long getVisitId() {
        return visitId;
    }

    public ProcedureItem getProcedure() {
        return procedure;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getPointsAtTime() {
        return pointsAtTime;
    }

    public Optional<Instant> getVoidedAt() {
        return Optional.ofNullable(voidedAt);
    }
}
