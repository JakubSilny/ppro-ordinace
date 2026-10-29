package cz.uhk.ordinace.audit.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import cz.uhk.ordinace.audit.domain.AuditEntry;
import cz.uhk.ordinace.audit.repository.AuditRepository;

@Service
public class AuditService {

    static final int MAX_ROWS = 1000;

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

    /**
     * Posledních 500 přístupů k údajům pacienta (karta, jeho rezervace a návštěvy), např. pro žádost podle čl. 15
     * GDPR. Hromadné přehledy (diář, výkaz) z cesty nepoznají, koho se týkaly, takže tu nejsou.
     */
    @Transactional(readOnly = true)
    public List<AuditEntry> forPatient(long patientId, Collection<Long> appointmentIds, Collection<Long> visitIds) {
        // id jsou čísla, do regulárního výrazu se tedy nic cizího nedostane
        String regex = "^/api/(patients/" + patientId + "|appointments/by-patient/" + patientId
                + anyOf("appointments", appointmentIds) + anyOf("visits", visitIds) + ")(/.*)?$";
        return entries.findByPathMatching(regex);
    }

    private static String anyOf(String prefix, Collection<Long> ids) {
        return ids.isEmpty() ? ""
                : "|" + prefix + "/(" + ids.stream().map(String::valueOf).collect(Collectors.joining("|")) + ")";
    }

    /** Celý záznam za období {@code from}–{@code to} včetně, nejnovější první; jen pro správce. */
    @Transactional(readOnly = true)
    public List<AuditEntry> search(LocalDate from, LocalDate to, String username) {
        ZoneId zone = clock.getZone();
        return entries.search(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant(),
                username.trim(), Limit.of(MAX_ROWS));
    }
}
