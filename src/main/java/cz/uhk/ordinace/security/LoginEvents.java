package cz.uhk.ordinace.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import cz.uhk.ordinace.audit.service.AuditService;

/** Počítá špatná hesla a každý pokus o přihlášení zapíše do záznamu přístupů. */
@Component
class LoginEvents {

    private final LoginAttempts attempts;
    private final AuditService audit;

    LoginEvents(LoginAttempts attempts, AuditService audit) {
        this.attempts = attempts;
        this.audit = audit;
    }

    @EventListener
    void success(AuthenticationSuccessEvent event) {
        attempts.succeeded(name(event));
        audit.record(name(event), "POST", "/login", 200);
    }

    @EventListener
    void badPassword(AuthenticationFailureBadCredentialsEvent event) {
        attempts.failed(name(event));
        audit.record(name(event), "POST", "/login", 401);
    }

    @EventListener
    void locked(AuthenticationFailureLockedEvent event) {
        audit.record(name(event), "POST", "/login", 423);
    }

    /** Jméno píše útočník, do sloupce se musí vejít. */
    private static String name(AbstractAuthenticationEvent event) {
        String name = event.getAuthentication().getName();
        return name.length() > 100 ? name.substring(0, 100) : name;
    }
}
