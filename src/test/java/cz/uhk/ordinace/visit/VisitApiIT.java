package cz.uhk.ordinace.visit;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import cz.uhk.ordinace.AbstractDbIT;

class VisitApiIT extends AbstractDbIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    /** Rezervace vložená přímo, protože přes API nejde založit do minulosti. */
    private long pastAppointment(String doctorLastName, Instant start, int patientOffset) {
        long doctor = jdbc.queryForObject("SELECT id FROM doctor WHERE last_name = ?", Long.class, doctorLastName);
        long patient = jdbc.queryForObject("SELECT id FROM patient ORDER BY id OFFSET ? LIMIT 1", Long.class,
                patientOffset);
        return jdbc.queryForObject("""
                INSERT INTO appointment (doctor_id, patient_id, start_at, end_at, status)
                VALUES (?, ?, ?, ?, 'BOOKED') RETURNING id
                """, Long.class, doctor, patient, Timestamp.from(start),
                Timestamp.from(start.plus(15, ChronoUnit.MINUTES)));
    }

    private long procedure(String code) {
        return jdbc.queryForObject("SELECT id FROM procedure_item WHERE code = ?", Long.class, code);
    }

    @Test
    void visitWithProceduresEndsUpInMonthlyReport() throws Exception {
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);
        long appointment = pastAppointment("Marešová", start, 60);

        String body = mvc.perform(post("/api/appointments/{id}/visit", appointment)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"kontrola tlaku\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long visit = ((Number) JsonPath.read(body, "$.id")).longValue();

        mvc.perform(post("/api/visits/{id}/procedures", visit).contentType(MediaType.APPLICATION_JSON)
                .content("{\"procedureId\":%d,\"quantity\":3}".formatted(procedure("09215"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.totalPoints").value(150));
        mvc.perform(post("/api/visits/{id}/procedures", visit).contentType(MediaType.APPLICATION_JSON)
                .content("{\"procedureId\":%d}".formatted(procedure("09115"))))
                .andExpect(jsonPath("$.totalPoints").value(330));
        mvc.perform(post("/api/visits/{id}/procedures", visit).contentType(MediaType.APPLICATION_JSON)
                .content("{\"procedureId\":%d}".formatted(procedure("09115"))))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/appointments/{id}", appointment)).andExpect(jsonPath("$.status").value("COMPLETED"));

        long doctor = jdbc.queryForObject("SELECT id FROM doctor WHERE last_name = 'Marešová'", Long.class);
        YearMonth month = YearMonth.from(start.atZone(ZoneId.of("Europe/Prague")));
        mvc.perform(get("/api/reports/monthly").param("doctorId", String.valueOf(doctor))
                .param("month", month.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.totalPoints").value(330));
        mvc.perform(get("/api/reports/monthly.csv").param("doctorId", String.valueOf(doctor))
                .param("month", month.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    @Test
    void visitCannotBeRecordedBeforeAppointmentStarts() throws Exception {
        long appointment = pastAppointment("Kolář", Instant.now().plus(3, ChronoUnit.DAYS), 61);

        mvc.perform(post("/api/appointments/{id}/visit", appointment).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void voidedProcedureDisappearsFromTotal() throws Exception {
        long appointment = pastAppointment("Vyhlídalová", Instant.now().minus(30, ChronoUnit.DAYS)
                .truncatedTo(ChronoUnit.MINUTES), 62);
        String body = mvc.perform(post("/api/appointments/{id}/visit", appointment)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getContentAsString();
        long visit = ((Number) JsonPath.read(body, "$.id")).longValue();
        body = mvc.perform(post("/api/visits/{id}/procedures", visit).contentType(MediaType.APPLICATION_JSON)
                .content("{\"procedureId\":%d}".formatted(procedure("09513"))))
                .andReturn().getResponse().getContentAsString();
        long item = ((Number) JsonPath.read(body, "$.procedures[0].id")).longValue();

        mvc.perform(post("/api/visits/{id}/procedures/{item}/void", visit, item))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(0))
                .andExpect(jsonPath("$.procedures[0].voidedAt").exists());
    }
}
