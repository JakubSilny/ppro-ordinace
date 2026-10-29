package cz.uhk.ordinace.audit.dto;

import java.time.Instant;

import cz.uhk.ordinace.audit.domain.AuditEntry;

public record AuditEntryResponse(Instant at, String username, String method, String path, int status) {

    public static AuditEntryResponse from(AuditEntry e) {
        return new AuditEntryResponse(e.getAt(), e.getUsername(), e.getMethod(), e.getPath(), e.getStatus());
    }
}
