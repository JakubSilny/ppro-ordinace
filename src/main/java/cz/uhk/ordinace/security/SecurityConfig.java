package cz.uhk.ordinace.security;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Přihlášení formulářem, účty z proměnné prostředí {@code APP_USERS} ve tvaru {@code jmeno:heslo,jmeno2:heslo2}.
 * API vrací 401 místo přesměrování; CSRF token jde v cookie XSRF-TOKEN a vrací se v hlavičce X-XSRF-TOKEN.
 */
@Configuration
public class SecurityConfig {

    private static final String CSP = "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; "
            + "font-src 'self'; connect-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'none'";

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/login.html", "/assets/**", "/favicon.svg").permitAll()
                .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login.html").loginProcessingUrl("/login")
                        .defaultSuccessUrl("/", true).failureUrl("/login.html?error").permitAll())
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login.html?logout")
                        .deleteCookies("JSESSIONID"))
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), new AntPathRequestMatcher("/api/**")))
                .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
                .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(CSP))
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.SAME_ORIGIN)));
        return http.build();
    }

    @Bean
    UserDetailsService users(@Value("${app.users}") String users, PasswordEncoder encoder) {
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        Arrays.stream(users.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(entry -> {
            int colon = entry.indexOf(':');
            if (colon < 1 || colon == entry.length() - 1) {
                throw new IllegalStateException("APP_USERS entries must look like name:password");
            }
            manager.createUser(User.withUsername(entry.substring(0, colon))
                    .password(encoder.encode(entry.substring(colon + 1))).roles("STAFF").build());
        });
        if (!manager.userExists(firstName(users))) {
            throw new IllegalStateException("Set APP_USERS, e.g. APP_USERS=recepce:a-long-password");
        }
        return manager;
    }

    private static String firstName(String users) {
        String first = users.split(",")[0].trim();
        return first.contains(":") ? first.substring(0, first.indexOf(':')) : "";
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
