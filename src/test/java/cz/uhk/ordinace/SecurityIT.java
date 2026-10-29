package cz.uhk.ordinace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
        anonymous.perform(get("/").accept(MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection());
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
    void repeatedWrongPasswordsLockTheNameEvenWithTheRightPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            anonymous.perform(formLogin("/login").user("zamek").password("wrong"))
                    .andExpect(redirectedUrl("/login.html?error"));
        }
        anonymous.perform(formLogin("/login").user("zamek").password("wrong"))
                .andExpect(redirectedUrl("/login.html?locked"));

        assertThat(jdbc.queryForList("SELECT status FROM audit_log WHERE username = 'zamek' AND path = '/login'",
                Integer.class)).containsOnly(401, 423).hasSize(6);
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

    @Test
    void accessLogIncludesReadsOfThePatientsAppointmentsButNotOthers() throws Exception {
        long patient = jdbc.queryForObject("SELECT id FROM patient ORDER BY id OFFSET 71 LIMIT 1", Long.class);
        long doctor = jdbc.queryForObject("SELECT id FROM doctor WHERE last_name = 'Kolář'", Long.class);
        long appointment = jdbc.queryForObject("""
                INSERT INTO appointment (doctor_id, patient_id, start_at, end_at, status)
                VALUES (?, ?, now() + interval '400 days', now() + interval '400 days 15 minutes', 'BOOKED')
                RETURNING id""", Long.class, doctor, patient);

        anonymous.perform(get("/api/appointments/{id}", appointment).with(user("olga"))).andExpect(status().isOk());
        anonymous.perform(get("/api/appointments/{id}", appointment * 10).with(user("olga")));

        anonymous.perform(get("/api/patients/{id}/access-log", patient).with(user("olga")))
                .andExpect(jsonPath("$[*].path", hasItem("/api/appointments/" + appointment)))
                .andExpect(jsonPath("$[*].path", not(hasItem("/api/appointments/" + appointment * 10))));
    }

    @Test
    void wholeAuditLogIsOnlyForAdmin() throws Exception {
        String today = LocalDate.now(ZoneId.of("Europe/Prague")).toString();
        anonymous.perform(get("/api/doctors").with(user("hana"))).andExpect(status().isOk());

        anonymous.perform(get("/api/audit").param("from", today).param("to", today).with(user("hana")))
                .andExpect(status().isForbidden());
        anonymous.perform(get("/api/audit").param("from", today).param("to", today).param("username", "hana")
                .with(user("spravce").roles("STAFF", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].path", hasItem("/api/doctors")))
                .andExpect(jsonPath("$[*].username", everyItem(is("hana"))));
        anonymous.perform(get("/api/me").with(user("spravce").roles("STAFF", "ADMIN")))
                .andExpect(jsonPath("$.admin").value(true));
        anonymous.perform(get("/api/me").with(user("hana"))).andExpect(jsonPath("$.admin").value(false));
    }
}
