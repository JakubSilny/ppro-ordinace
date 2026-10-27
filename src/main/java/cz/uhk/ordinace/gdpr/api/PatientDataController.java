package cz.uhk.ordinace.gdpr.api;

import java.time.Clock;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cz.uhk.ordinace.gdpr.dto.AccessLogResponse;
import cz.uhk.ordinace.gdpr.dto.PatientDataResponse;
import cz.uhk.ordinace.gdpr.service.PatientDataService;

@RestController
@RequestMapping("/api/patients/{id}")
public class PatientDataController {

    private final PatientDataService data;
    private final Clock clock;

    public PatientDataController(PatientDataService data, Clock clock) {
        this.data = data;
        this.clock = clock;
    }

    @GetMapping("/export")
    ResponseEntity<PatientDataResponse> export(@PathVariable long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pacient-" + id + ".json\"")
                .body(PatientDataResponse.from(data.export(id), clock.instant()));
    }

    @GetMapping("/access-log")
    List<AccessLogResponse> accessLog(@PathVariable long id) {
        return data.accessLog(id).stream().map(AccessLogResponse::from).toList();
    }
}
