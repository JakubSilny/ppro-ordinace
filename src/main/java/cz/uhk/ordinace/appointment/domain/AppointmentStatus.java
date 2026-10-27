package cz.uhk.ordinace.appointment.domain;

import java.util.List;

public enum AppointmentStatus {
    BOOKED, COMPLETED, CANCELLED, NO_SHOW, RESCHEDULED;

    /** Stavy, které obsazují termín lékaři i pacientovi (shodně s exclusion constraintem v DB). */
    public static final List<AppointmentStatus> OCCUPYING = List.of(BOOKED, COMPLETED, NO_SHOW);
}
