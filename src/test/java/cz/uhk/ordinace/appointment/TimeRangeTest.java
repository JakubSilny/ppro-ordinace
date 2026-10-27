package cz.uhk.ordinace.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class TimeRangeTest {

    private static TimeRange r(String from, String to) {
        return new TimeRange(Instant.parse("2026-10-05T" + from + ":00Z"), Instant.parse("2026-10-05T" + to + ":00Z"));
    }

    @Test
    void busyBlocksSplitOpenIntervalAndShortGapsAreDropped() {
        List<TimeRange> free = TimeRange.free(List.of(r("08:00", "12:00")),
                List.of(r("09:00", "09:30"), r("09:40", "10:00"), r("11:00", "11:15")), Duration.ofMinutes(15));

        assertThat(free).containsExactly(r("08:00", "09:00"), r("10:00", "11:00"), r("11:15", "12:00"));
    }

    @Test
    void overlappingBusyBlocksAndBlocksOutsideAreHandled() {
        List<TimeRange> free = TimeRange.free(List.of(r("13:00", "16:00"), r("08:00", "12:00")),
                List.of(r("07:00", "08:30"), r("10:00", "11:00"), r("10:30", "10:45"), r("15:30", "17:00")),
                Duration.ofMinutes(15));

        assertThat(free).containsExactly(r("08:30", "10:00"), r("11:00", "12:00"), r("13:00", "15:30"));
    }

    @Test
    void fullyBusyIntervalHasNoWindow() {
        assertThat(TimeRange.free(List.of(r("08:00", "09:00")), List.of(r("07:00", "10:00")), Duration.ofMinutes(5)))
                .isEmpty();
    }
}
