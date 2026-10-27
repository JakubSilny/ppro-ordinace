package cz.uhk.ordinace.appointment.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record TimeRange(Instant startAt, Instant endAt) {

    /** Souvislá okna z {@code open}, která nezasahují do {@code busy} a jsou dlouhá aspoň {@code min}. */
    public static List<TimeRange> free(List<TimeRange> open, List<TimeRange> busy, Duration min) {
        Comparator<TimeRange> byStart = Comparator.comparing(TimeRange::startAt);
        List<TimeRange> sortedBusy = busy.stream().sorted(byStart).toList();
        List<TimeRange> result = new ArrayList<>();
        for (TimeRange o : open.stream().sorted(byStart).toList()) {
            Instant cursor = o.startAt();
            for (TimeRange b : sortedBusy) {
                if (!b.endAt().isAfter(cursor) || !b.startAt().isBefore(o.endAt())) {
                    continue;
                }
                addIfLongEnough(result, cursor, b.startAt(), min);
                cursor = b.endAt();
            }
            addIfLongEnough(result, cursor, o.endAt(), min);
        }
        return result;
    }

    private static void addIfLongEnough(List<TimeRange> result, Instant start, Instant end, Duration min) {
        if (Duration.between(start, end).compareTo(min) >= 0) {
            result.add(new TimeRange(start, end));
        }
    }
}
