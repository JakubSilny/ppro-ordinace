package cz.uhk.ordinace.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Po {@value #MAX_FAILURES} špatných heslech za sebou se jméno na {@link #LOCK} zamkne (proti hádání hesla).
 * Zamyká se i neexistující jméno, aby se podle hlášky nedalo poznat, které účty existují.
 */
@Component
class LoginAttempts {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK = Duration.ofMinutes(5);

    private record Failures(int count, Instant last) {
    }

    // ponytail: v paměti, po restartu se zapomene; při víc instancích aplikace přesunout do DB
    private final Map<String, Failures> byUser = new ConcurrentHashMap<>();
    private final Clock clock;

    LoginAttempts(Clock clock) {
        this.clock = clock;
    }

    boolean isLocked(String username) {
        Failures f = byUser.get(key(username));
        return f != null && f.count() >= MAX_FAILURES && !expired(f);
    }

    void failed(String username) {
        byUser.values().removeIf(this::expired); // jinak by mapu nafouklo hádání náhodných jmen
        byUser.compute(key(username), (k, f) -> f == null || expired(f)
                ? new Failures(1, clock.instant())
                : new Failures(f.count() + 1, clock.instant()));
    }

    void succeeded(String username) {
        byUser.remove(key(username));
    }

    int trackedNames() {
        return byUser.size();
    }

    private boolean expired(Failures f) {
        return !clock.instant().isBefore(f.last().plus(LOCK));
    }

    private static String key(String username) {
        return username.trim().toLowerCase();
    }
}
