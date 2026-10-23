package cz.uhk.ordinace;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class OrdinaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrdinaceApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Europe/Prague"));
    }
}
