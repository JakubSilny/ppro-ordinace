package cz.uhk.ordinace.visit.service;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import cz.uhk.ordinace.visit.repository.ReportLine;

/** Výkaz lékaře za měsíc: jednotlivé výkony, souhrn po kódu a celkový součet bodů. */
public record MonthlyReport(long doctorId, YearMonth month, List<ReportLine> lines, List<Summary> summary,
        long totalPoints) {

    public record Summary(String code, String name, long quantity, long points) {
    }

    public static MonthlyReport of(long doctorId, YearMonth month, List<ReportLine> lines) {
        Map<String, Summary> byCode = new TreeMap<>();
        for (ReportLine l : lines) {
            byCode.merge(l.code(), new Summary(l.code(), l.name(), l.quantity(), l.points()),
                    (a, b) -> new Summary(a.code(), a.name(), a.quantity() + b.quantity(), a.points() + b.points()));
        }
        long total = lines.stream().mapToLong(ReportLine::points).sum();
        return new MonthlyReport(doctorId, month, lines, List.copyOf(byCode.values()), total);
    }
}
