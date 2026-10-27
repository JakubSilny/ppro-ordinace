package cz.uhk.ordinace.audit.domain;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_log")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant at;

    private String username;

    private String method;

    private String path;

    private int status;

    protected AuditEntry() {
    }

    public AuditEntry(Instant at, String username, String method, String path, int status) {
        this.at = at;
        this.username = username;
        this.method = method;
        this.path = path;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Instant getAt() {
        return at;
    }

    public String getUsername() {
        return username;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public int getStatus() {
        return status;
    }
}
