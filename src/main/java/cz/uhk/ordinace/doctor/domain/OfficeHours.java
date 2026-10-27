package cz.uhk.ordinace.doctor.domain;

import java.time.LocalTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Jeden interval týdenní šablony; weekday 1 = pondělí … 7 = neděle. */
@Entity
@Table(name = "office_hours")
public class OfficeHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long doctorId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int weekday;

    private LocalTime fromTime;

    private LocalTime toTime;

    private String location;

    private boolean bookable;

    protected OfficeHours() {
    }

    public OfficeHours(long doctorId, int weekday, LocalTime fromTime, LocalTime toTime, String location,
            boolean bookable) {
        this.doctorId = doctorId;
        this.weekday = weekday;
        this.fromTime = fromTime;
        this.toTime = toTime;
        this.location = location;
        this.bookable = bookable;
    }

    public Long getId() {
        return id;
    }

    public long getDoctorId() {
        return doctorId;
    }

    public int getWeekday() {
        return weekday;
    }

    public LocalTime getFromTime() {
        return fromTime;
    }

    public LocalTime getToTime() {
        return toTime;
    }

    public String getLocation() {
        return location;
    }

    public boolean isBookable() {
        return bookable;
    }
}
