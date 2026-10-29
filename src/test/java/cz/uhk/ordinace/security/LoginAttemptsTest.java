package cz.uhk.ordinace.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class LoginAttemptsTest {

    private Instant now = Instant.parse("2026-10-27T08:00:00Z");

    private final LoginAttempts attempts = new LoginAttempts(new Clock() {
        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    });

    private void fail(String user, int times) {
        for (int i = 0; i < times; i++) {
            attempts.failed(user);
        }
    }

    @Test
    void locksAfterFiveFailuresAndOnlyThatName() {
        fail("vera", 4);
        assertThat(attempts.isLocked("vera")).isFalse();

        attempts.failed("Vera ");

        assertThat(attempts.isLocked("vera")).isTrue();
        assertThat(attempts.isLocked("recepce")).isFalse();
    }

    @Test
    void lockExpiresAndCountingStartsOver() {
        fail("vera", 5);

        now = now.plus(LoginAttempts.LOCK);

        assertThat(attempts.isLocked("vera")).isFalse();
        attempts.failed("vera");
        assertThat(attempts.isLocked("vera")).isFalse();
    }

    @Test
    void successfulLoginResetsTheCount() {
        fail("vera", 4);
        attempts.succeeded("vera");
        fail("vera", 4);

        assertThat(attempts.isLocked("vera")).isFalse();
    }

    @Test
    void oldFailuresDoNotAddUp() {
        fail("vera", 4);
        now = now.plus(LoginAttempts.LOCK).plus(Duration.ofSeconds(1));
        attempts.failed("vera");

        assertThat(attempts.isLocked("vera")).isFalse();
    }

    @Test
    void expiredNamesAreForgottenSoGuessingNamesCannotFillMemory() {
        fail("nahodne1", 1);
        fail("nahodne2", 1);
        now = now.plus(LoginAttempts.LOCK);
        attempts.failed("nahodne3");

        assertThat(attempts.trackedNames()).isEqualTo(1);
    }
}
