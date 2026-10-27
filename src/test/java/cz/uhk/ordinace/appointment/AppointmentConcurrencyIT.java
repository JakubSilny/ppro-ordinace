package cz.uhk.ordinace.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import cz.uhk.ordinace.AbstractDbIT;
import cz.uhk.ordinace.appointment.dto.CreateAppointmentRequest;
import cz.uhk.ordinace.appointment.service.AppointmentService;
import cz.uhk.ordinace.common.ConflictException;

class AppointmentConcurrencyIT extends AbstractDbIT {

    @Autowired
    AppointmentService service;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void twoSimultaneousRequestsForTheSameSlotHaveOneWinner() throws Exception {
        long doctor = jdbc.queryForObject("SELECT id FROM doctor WHERE last_name = 'Kolář'", Long.class);
        long patientA = jdbc.queryForObject("SELECT id FROM patient ORDER BY id OFFSET 40 LIMIT 1", Long.class);
        long patientB = jdbc.queryForObject("SELECT id FROM patient ORDER BY id OFFSET 41 LIMIT 1", Long.class);
        LocalDate monday = LocalDate.now(ZoneId.of("Europe/Prague")).plusWeeks(9)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        var start = ZonedDateTime.of(monday, LocalTime.of(7, 0), ZoneId.of("Europe/Prague")).toInstant();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> results = new ArrayList<>();
        for (long patient : new long[] { patientA, patientB }) {
            Callable<Boolean> task = () -> {
                barrier.await();
                try {
                    service.create(new CreateAppointmentRequest(doctor, patient, start, 15, null));
                    return true;
                } catch (ConflictException | DataIntegrityViolationException e) {
                    return false;
                }
            };
            results.add(pool.submit(task));
        }
        long winners = 0;
        for (Future<Boolean> f : results) {
            winners += f.get() ? 1 : 0;
        }
        pool.shutdown();

        assertThat(winners).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointment WHERE doctor_id = ? AND start_at = ?",
                Integer.class, doctor, java.sql.Timestamp.from(start))).isEqualTo(1);
    }
}
