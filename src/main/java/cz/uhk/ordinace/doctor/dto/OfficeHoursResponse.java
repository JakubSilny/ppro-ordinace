package cz.uhk.ordinace.doctor.dto;

import java.time.LocalTime;

import cz.uhk.ordinace.doctor.domain.OfficeHours;

public record OfficeHoursResponse(int weekday, LocalTime from, LocalTime to, String location, boolean bookable) {

    public static OfficeHoursResponse from(OfficeHours h) {
        return new OfficeHoursResponse(h.getWeekday(), h.getFromTime(), h.getToTime(), h.getLocation(),
                h.isBookable());
    }
}
