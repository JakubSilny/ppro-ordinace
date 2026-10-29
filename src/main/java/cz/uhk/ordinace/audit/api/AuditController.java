package cz.uhk.ordinace.audit.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cz.uhk.ordinace.audit.dto.AuditEntryResponse;
import cz.uhk.ordinace.audit.service.AuditService;

/** Celý záznam přístupů pro správce (role ADMIN, viz SecurityConfig). */
@RestController
@RequestMapping("/api/audit")
class AuditController {

    private final AuditService audit;

    AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    List<AuditEntryResponse> search(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "") String username) {
        return audit.search(from, to, username).stream().map(AuditEntryResponse::from).toList();
    }
}
