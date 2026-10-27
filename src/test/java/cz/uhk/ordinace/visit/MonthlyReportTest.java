package cz.uhk.ordinace.visit.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import cz.uhk.ordinace.visit.repository.ReportLine;

class MonthlyReportTest {

    private static ReportLine line(String code, int quantity, int points) {
        return new ReportLine(Instant.parse("2026-10-05T07:00:00Z"), "Jan", "Novák", "7000000001", "111", code,
                "Výkon " + code, quantity, points);
    }

    @Test
    void summarisesByCodeWithFrozenPoints() {
        // stejný kód se dvěma různými body: cena v číselníku se mezi návštěvami změnila
        MonthlyReport report = MonthlyReport.of(1, YearMonth.of(2026, 10), List.of(
                line("09215", 3, 50), line("09115", 1, 180), line("09215", 2, 55)));

        assertThat(report.summary()).containsExactly(
                new MonthlyReport.Summary("09115", "Výkon 09115", 1, 180),
                new MonthlyReport.Summary("09215", "Výkon 09215", 5, 260));
        assertThat(report.totalPoints()).isEqualTo(440);
    }

    @Test
    void emptyMonthHasZeroTotal() {
        MonthlyReport report = MonthlyReport.of(1, YearMonth.of(2026, 10), List.of());

        assertThat(report.summary()).isEmpty();
        assertThat(report.totalPoints()).isZero();
    }
}
