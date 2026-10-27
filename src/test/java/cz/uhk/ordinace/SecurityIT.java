package cz.uhk.ordinace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Bez výchozího přihlášení z {@link AbstractDbIT}: ověřuje přístup tak, jak ho vidí prohlížeč. */
class SecurityIT extends AbstractDbIT {

    @Autowired
    WebApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;

    MockMvc anonymous;

    @BeforeEach
    void setUp() {
        anonymous = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void apiRequiresLogin() throws Exception {
        anonymous.perform(get("/api/doctors")).andExpect(status().isUnauthorized());
        anonymous.perform(get("/")).andExpect(status().is3xxRedirection());
        anonymous.perform(get("/login.html")).andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void loginWithConfiguredUserWorksAndWrongPasswordFails() throws Exception {
        anonymous.perform(formLogin("/login").user("recepce").password("test-password"))
                .andExpect(redirectedUrl("/"));
        anonymous.perform(formLogin("/login").user("recepce").password("wrong"))
                .andExpect(redirectedUrl("/login.html?error"));
    }

    @Test
    void writeWithoutCsrfTokenIsRejected() throws Exception {
        anonymous.perform(post("/api/doctors").with(user("recepce")).contentType("application/json")
                .content("{\"firstName\":\"A\",\"lastName\":\"B\"}")).andExpect(status().isForbidden());
    }

    @Test
    void patientAccessIsAuditedAndExportable() throws Exception {
        long patient = jdbc.queryForObject("SELECT id FROM patient ORDER BY id OFFSET 70 LIMIT 1", Long.class);

        anonymous.perform(get("/api/patients/{id}", patient).with(user("vera"))).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE username = 'vera' AND path = ?",
                Integer.class, "/api/patients/" + patient)).isEqualTo(1);
        anonymous.perform(get("/api/patients/{id}/export", patient).with(user("vera")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.id").value(patient))
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
        anonymous.perform(get("/api/patients/{id}/access-log", patient).with(user("vera")))
                .andExpect(jsonPath("$[0].username").value("vera"));
    }
}
