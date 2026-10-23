package cz.uhk.ordinace.doctor;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import cz.uhk.ordinace.AbstractDbIT;

class DoctorApiIT extends AbstractDbIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    private long createDoctor() throws Exception {
        String body = mvc.perform(post("/api/doctors").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Test\",\"lastName\":\"Lékař\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void officeHoursAreReplacedAndOverlapIsRejected() throws Exception {
        long id = createDoctor();
        String ok = """
                [{"weekday":1,"from":"08:00","to":"12:00","location":"Na Vyhlídce","bookable":true},
                 {"weekday":2,"from":"08:00","to":"12:00","location":"Poliklinika Jih","bookable":false}]""";
        String overlap = """
                [{"weekday":1,"from":"08:00","to":"12:00","location":"A","bookable":true},
                 {"weekday":1,"from":"11:00","to":"13:00","location":"A","bookable":true}]""";

        mvc.perform(put("/api/doctors/{id}/office-hours", id).contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isOk());
        mvc.perform(put("/api/doctors/{id}/office-hours", id).contentType(MediaType.APPLICATION_JSON)
                .content(overlap)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/doctors/{id}/office-hours", id))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].bookable").value(false));
    }

    @Test
    void absenceFlagsOnlyBookedAppointmentsInItsRange() throws Exception {
        long doctor = createDoctor();
        long patient = jdbc.queryForObject("SELECT max(id) FROM patient", Long.class);
        jdbc.update("""
                INSERT INTO appointment (doctor_id, patient_id, start_at, end_at, status) VALUES
                (?, ?, '2031-03-04 08:00+01', '2031-03-04 08:30+01', 'BOOKED'),
                (?, ?, '2031-03-04 09:00+01', '2031-03-04 09:30+01', 'CANCELLED'),
                (?, ?, '2031-03-06 08:00+01', '2031-03-06 08:30+01', 'BOOKED')
                """, doctor, patient, doctor, patient, doctor, patient);

        mvc.perform(post("/api/doctors/{id}/absences", doctor).contentType(MediaType.APPLICATION_JSON).content("""
                {"startAt":"2031-03-04T00:00:00+01:00","endAt":"2031-03-05T00:00:00+01:00","reason":"Flu"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flaggedAppointments").value(1));

        Assertions.assertThat(jdbc.queryForList(
                "SELECT status FROM appointment WHERE doctor_id = ? AND needs_rebooking", String.class, doctor))
                .containsExactly("BOOKED");
    }
}
