package cz.uhk.ordinace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Vlastní databáze: ukázková data by kolidovala s termíny ostatních integračních testů. */
@SpringBootTest(properties = "app.users=recepce:test-password")
@ActiveProfiles("demo")
class DemoDataIT {

    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        DB.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void demoDataCoversAllScreens() {
        assertThat(count("SELECT count(*) FROM appointment WHERE status = 'COMPLETED'")).isGreaterThan(100);
        assertThat(count("SELECT count(*) FROM appointment WHERE status = 'BOOKED' AND start_at > now()")).isPositive();
        assertThat(count("SELECT count(*) FROM appointment WHERE status IN ('CANCELLED', 'NO_SHOW')")).isPositive();
        assertThat(count("SELECT count(*) FROM visit_procedure")).isGreaterThan(100);
        assertThat(count("""
                SELECT count(*) FROM appointment a JOIN patient p ON p.id = a.patient_id
                WHERE p.last_name = 'Krátká' AND a.status IN ('CANCELLED', 'NO_SHOW')
                """)).isGreaterThanOrEqualTo(3);
    }

    @Test
    void demoDataFollowsBookingRules() {
        // každá obsazující rezervace leží celá v jednom bookovatelném intervalu ordinačních hodin
        assertThat(count("""
                SELECT count(*) FROM appointment a
                WHERE a.status IN ('BOOKED', 'COMPLETED', 'NO_SHOW') AND NOT EXISTS (
                    SELECT 1 FROM office_hours o
                    WHERE o.doctor_id = a.doctor_id AND o.bookable
                      AND o.weekday = extract(isodow FROM a.start_at AT TIME ZONE 'Europe/Prague')
                      AND (a.start_at AT TIME ZONE 'Europe/Prague')::time >= o.from_time
                      AND (a.end_at AT TIME ZONE 'Europe/Prague')::time <= o.to_time)
                """)).isZero();
        // návštěva jen u dokončené rezervace, která už začala
        assertThat(count("""
                SELECT count(*) FROM visit v JOIN appointment a ON a.id = v.appointment_id
                WHERE a.status <> 'COMPLETED' OR a.start_at > now()
                """)).isZero();
        // k přeobjednání jen objednané termíny v době nepřítomnosti
        assertThat(count("""
                SELECT count(*) FROM appointment a WHERE a.needs_rebooking AND (a.status <> 'BOOKED' OR NOT EXISTS (
                    SELECT 1 FROM doctor_absence ab
                    WHERE ab.doctor_id = a.doctor_id AND ab.start_at < a.end_at AND ab.end_at > a.start_at))
                """)).isZero();
    }
}
