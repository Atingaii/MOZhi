package cn.zy.mozhi.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = FlywayBaselineIntegrationTest.FlywayTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.url=jdbc:h2:mem:mozhi;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,"
                        + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration"
        }
)
@ActiveProfiles("test")
class FlywayBaselineIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class FlywayTestApplication {
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_apply_phase0_flyway_baseline_on_application_startup() {
        Integer historyCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true",
                Integer.class
        );
        Integer markerCount = jdbcTemplate.queryForObject(
                "select count(*) from mozhi_bootstrap_marker where marker_key = 'phase0-baseline'",
                Integer.class
        );

        assertThat(historyCount).isGreaterThan(0);
        assertThat(markerCount).isEqualTo(1);
    }
}
