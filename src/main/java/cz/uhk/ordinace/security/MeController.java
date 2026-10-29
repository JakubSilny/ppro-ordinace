package cz.uhk.ordinace.security;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    /** {@code admin} jen řídí, co stránka ukáže; přístup k /api/audit hlídá SecurityConfig. */
    @GetMapping("/api/me")
    Map<String, Object> me(Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return Map.of("username", auth.getName(), "admin", admin);
    }
}
