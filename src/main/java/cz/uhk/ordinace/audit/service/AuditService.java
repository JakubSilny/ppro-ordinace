package cz.uhk.ordinace.audit.service;

import java.time.Clock;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import cz.uhk.ordinace.audit.domain.AuditEntry;
import cz.uhk.ordinace.audit.repository.AuditRepository;

@Service
public class AuditService {

    private final AuditRepository entries;
    private final Clock clock;

    public AuditService(AuditRepository entries, Clock clock) {
        this.entries = entries;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String username, String method, String path, int status) {
        entries.save(new AuditEntry(clock.instant(), username, method, path, status));
    }

    /** Poslední přístupy k záznamu pacienta, např. pro jeho žádost podle čl. 15 GDPR. */
    @Transactional(readOnly = true)
    public List<AuditEntry> forPatient(long patientId) {
        return entries.findForPaths("/api/patients/" + patientId, "/api/appointments/by-patient/" + patientId,
                Limit.of(500));
    }
}
