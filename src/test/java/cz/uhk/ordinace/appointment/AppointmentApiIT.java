package cz.uhk.ordinace.appointment;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import cz.uhk.ordinace.AbstractDbIT;

class AppointmentApiIT extends AbstractDbIT {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");

    /** Pondělí za osm týdnů: seed lékaři Kolář i Vyhlídalová mají v pondělí bookable hodiny. */
    private static final LocalDate MONDAY = LocalDate.now(PRAGUE).plusWeeks(8)
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    private long doctor(String lastName) {
        return jdbc.queryForObject("SELECT id FROM doctor WHERE last_name = ?", Long.class, lastName);
    }

    private long patient(int offset) {
        return jdbc.queryForObject("SELECT id FROM patient ORDER BY id OFFSET ? LIMIT 1", Long.class, offset);
    }

    private Instant at(String time) {
        return ZonedDateTime.of(MONDAY, LocalTime.parse(time), PRAGUE).toInstant();
    }

    private ResultActions book(long doctor, long patient, String time, int minutes) throws Exception {
        return mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(
                "{\"doctorId\":%d,\"patientId\":%d,\"startAt\":\"%s\",\"durationMinutes\":%d}"
                        .formatted(doctor, patient, at(time), minutes)));
    }

    private long idOf(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void backToBackAppointmentsPassButOverlapIsConflict() throws Exception {
        long doctor = doctor("Kolář");
        book(doctor, patient(20), "07:00", 15).andExpect(status().isCreated());
        book(doctor, patient(21), "07:15", 15).andExpect(status().isCreated());

        book(doctor, patient(22), "07:10", 15).andExpect(status().isConflict());
    }

    @Test
    void patientCannotBeBookedWithTwoDoctorsAtOnce() throws Exception {
        book(doctor("Kolář"), patient(23), "09:00", 15).andExpect(status().isCreated());

        book(doctor("Vyhlídalová"), patient(23), "09:00", 15).andExpect(status().isConflict());
    }

    @Test
    void outsideOfficeHoursIsUnprocessable() throws Exception {
        book(doctor("Kolář"), patient(24), "11:30", 15).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cancelledSlotCanBeBookedAgainAndDisappearsFromDiary() throws Exception {
        long doctor = doctor("Vyhlídalová");
        long first = idOf(book(doctor, patient(25), "10:00", 15).andExpect(status().isCreated()));

        mvc.perform(post("/api/appointments/{id}/cancel", first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        book(doctor, patient(26), "10:00", 15).andExpect(status().isCreated());

        mvc.perform(get("/api/appointments").param("doctorId", String.valueOf(doctor))
                .param("from", MONDAY.toString()).param("to", MONDAY.toString()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].patientId").value(patient(26)));
    }

    @Test
    void rescheduleKeepsHistoryAndFreesOldSlot() throws Exception {
        long doctor = doctor("Vyhlídalová");
        long old = idOf(book(doctor, patient(27), "08:00", 30).andExpect(status().isCreated()));

        mvc.perform(post("/api/appointments/{id}/reschedule", old).contentType(MediaType.APPLICATION_JSON)
                .content("{\"startAt\":\"%s\"}".formatted(at("08:30"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rescheduledFromId").value(old))
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.startAt").exists());
        mvc.perform(get("/api/appointments/{id}", old)).andExpect(jsonPath("$.status").value("RESCHEDULED"));
        book(doctor, patient(28), "08:00", 30).andExpect(status().isCreated());
    }
}
