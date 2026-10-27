package cz.uhk.ordinace.visit.api;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cz.uhk.ordinace.visit.dto.ReportLine;
import cz.uhk.ordinace.visit.service.MonthlyReport;
import cz.uhk.ordinace.visit.service.VisitService;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.yyyy")
            .withZone(ZoneId.of("Europe/Prague"));

    private final VisitService visits;

    public ReportController(VisitService visits) {
        this.visits = visits;
    }

    @GetMapping("/monthly")
    MonthlyReport monthly(@RequestParam long doctorId, @RequestParam YearMonth month) {
        return visits.monthlyReport(doctorId, month);
    }

    /** CSV se středníkem a BOM, aby ho Excel v české lokalizaci otevřel správně. */
    @GetMapping("/monthly.csv")
    ResponseEntity<String> monthlyCsv(@RequestParam long doctorId, @RequestParam YearMonth month) {
        MonthlyReport report = visits.monthlyReport(doctorId, month);
        String header = "Datum;Příjmení;Jméno;Číslo pojištěnce;Pojišťovna;Kód výkonu;Výkon;Počet;Body\n";
        String body = report.lines().stream().map(ReportController::csvRow).collect(Collectors.joining());
        String footer = ";;;;;;Celkem;;" + report.totalPoints() + "\n";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"vykaz-" + month + "-lekar-" + doctorId + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body("﻿" + header + body + footer);
    }

    private static String csvRow(ReportLine l) {
        return String.join(";", DATE.format(l.performedAt()), csv(l.patientLastName()), csv(l.patientFirstName()),
                csv(l.insuranceId()), csv(l.insurerCode()), csv(l.code()), csv(l.name()),
                String.valueOf(l.quantity()), String.valueOf(l.points())) + "\n";
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = value.matches("^[=+\\-@].*") ? "'" + value : value;
        return v.contains(";") || v.contains("\"") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }
}
