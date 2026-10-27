package cz.uhk.ordinace.audit.api;

import java.security.Principal;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import cz.uhk.ordinace.audit.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Zapisuje každé volání API s osobními údaji; dotaz (např. hledané jméno) se neukládá. */
@Component
class AuditInterceptor implements HandlerInterceptor {

    private final AuditService audit;

    AuditInterceptor(AuditService audit) {
        this.audit = audit;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        Principal user = request.getUserPrincipal();
        if (user != null) {
            audit.record(user.getName(), request.getMethod(), request.getRequestURI(), response.getStatus());
        }
    }
}
