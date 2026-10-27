package cz.uhk.ordinace;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Jeden Postgres kontejner sdílený všemi integračními testy; MockMvc volá API jako přihlášená recepce. */
@SpringBootTest(properties = "app.users=recepce:test-password")
@AutoConfigureMockMvc
@Import(AbstractDbIT.LoggedIn.class)
public abstract class AbstractDbIT {

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

    @TestConfiguration
    static class LoggedIn {

        @Bean
        MockMvcBuilderCustomizer loggedInReception() {
            return builder -> builder.defaultRequest(get("/").with(user("recepce")).with(csrf()));
        }
    }
}
