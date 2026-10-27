package cz.uhk.ordinace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class SchemaConstraintsIT extends AbstractDbIT {

    private static final String INSERT = """
            INSERT INTO appointment (doctor_id, patient_id, start_at, end_at, status)
            VALUES (?, ?, ?::timestamptz, ?::timestamptz, ?)
            """;

    @Autowired
    JdbcTemplate jdbc;

    long doctorA;
    long doctorB;
    long patientA;
    long patientB;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM visit_procedure");
        jdbc.update("DELETE FROM visit");
        jdbc.update("DELETE FROM appointment");
        doctorA = id("SELECT id FROM doctor ORDER BY id OFFSET 0 LIMIT 1");
        doctorB = id("SELECT id FROM doctor ORDER BY id OFFSET 1 LIMIT 1");
        patientA = id("SELECT id FROM patient ORDER BY id OFFSET 0 LIMIT 1");
        patientB = id("SELECT id FROM patient ORDER BY id OFFSET 1 LIMIT 1");
    }

    @Test
    void doctorCannotHaveOverlappingAppointments() {
        book(doctorA, patientA, "2030-01-07 08:00+01", "2030-01-07 08:30+01", "BOOKED");

        assertThatThrownBy(() -> book(doctorA, patientB, "2030-01-07 08:15+01", "2030-01-07 08:45+01", "BOOKED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void patientCannotHaveOverlappingAppointmentsEvenWithDifferentDoctors() {
        book(doctorA, patientA, "2030-01-07 08:00+01", "2030-01-07 08:30+01", "BOOKED");

        assertThatThrownBy(() -> book(doctorB, patientA, "2030-01-07 08:15+01", "2030-01-07 08:45+01", "BOOKED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void backToBackAppointmentsAreAllowed() {
        book(doctorA, patientA, "2030-01-07 08:00+01", "2030-01-07 08:30+01", "BOOKED");
        book(doctorA, patientB, "2030-01-07 08:30+01", "2030-01-07 09:00+01", "BOOKED");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointment", Integer.class)).isEqualTo(2);
    }

    @Test
    void cancelledAppointmentFreesTheSlot() {
        book(doctorA, patientA, "2030-01-07 08:00+01", "2030-01-07 08:30+01", "CANCELLED");
        book(doctorA, patientA, "2030-01-07 08:00+01", "2030-01-07 08:30+01", "BOOKED");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointment", Integer.class)).isEqualTo(2);
    }

    @Test
    void duplicateInsuranceIdIsRejected() {
        String insuranceId = jdbc.queryForObject(
                "SELECT insurance_id FROM patient WHERE insurance_id IS NOT NULL LIMIT 1", String.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO patient (first_name, last_name, birth_year, phone, insurance_id) VALUES ('A', 'B', 1980, '123', ?)",
                insuranceId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditLogCannotBeChangedOrDeleted() {
        jdbc.update("INSERT INTO audit_log (username, method, path, status) VALUES ('vera', 'GET', '/api/x', 200)");

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET username = 'nikdo'"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_log"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private void book(long doctor, long patient, String from, String to, String status) {
        jdbc.update(INSERT, doctor, patient, from, to, status);
    }
}
