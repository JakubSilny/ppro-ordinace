package cz.uhk.ordinace.gdpr.dto;

import java.time.Instant;

import cz.uhk.ordinace.audit.domain.AuditEntry;

public record AccessLogResponse(Instant at, String username, String method, String path, int status) {

    public static AccessLogResponse from(AuditEntry e) {
        return new AccessLogResponse(e.getAt(), e.getUsername(), e.getMethod(), e.getPath(), e.getStatus());
    }
}
