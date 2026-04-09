package cn.zy.mozhi.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = DraftFlywayIntegrationTest.DraftFlywayTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.url=jdbc:h2:mem:mozhi-draft-flyway;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,"
                        + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration"
        }
)
@ActiveProfiles("test")
class DraftFlywayIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class DraftFlywayTestApplication {
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_create_foundational_content_tables_for_phase2_bootstrap() {
        Integer draftCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'draft'",
                Integer.class
        );
        Integer noteCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'note'",
                Integer.class
        );
        Integer mediaRefCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'media_ref'",
                Integer.class
        );
        Integer draftVersionColumnCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_name = 'draft' and column_name = 'version'",
                Integer.class
        );

        assertThat(draftCount).isEqualTo(1);
        assertThat(noteCount).isEqualTo(1);
        assertThat(mediaRefCount).isEqualTo(1);
        assertThat(draftVersionColumnCount).isEqualTo(1);
    }
}
