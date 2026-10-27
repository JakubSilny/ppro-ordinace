package cz.uhk.ordinace.visit.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Položka číselníku výkonů; {@code countable} = u výkonu se zapisuje počet. */
@Entity
@Table(name = "procedure_item")
public class ProcedureItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    private String name;

    private int points;

    private boolean countable;

    protected ProcedureItem() {
    }

    public ProcedureItem(String code, String name, int points, boolean countable) {
        this.code = code;
        this.name = name;
        this.points = points;
        this.countable = countable;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getPoints() {
        return points;
    }

    public boolean isCountable() {
        return countable;
    }
}
