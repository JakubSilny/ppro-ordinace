package cz.uhk.ordinace.security;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Doplňuje přihlášeného uživatele do polí {@code @CreatedBy} a {@code @LastModifiedBy}. */
@Configuration
@EnableJpaAuditing
class AuditorConfig {

    @Bean
    AuditorAware<String> currentUser() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated).map(Authentication::getName);
    }
}
