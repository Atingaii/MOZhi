package cn.zy.mozhi.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendRuntimeEnvironmentVerifierTest {

    @Test
    void should_keep_local_runtime_environment_contract() throws IOException {
        Path moduleRoot = Path.of("").toAbsolutePath();
        Path backendRoot = moduleRoot.getParent();
        Path repoRoot = backendRoot.getParent();

        String infrastructurePom = Files.readString(backendRoot.resolve("mozhi-infrastructure").resolve("pom.xml"));
        Path mysqlBootstrapSql = repoRoot.resolve("docs").resolve("dev-ops").resolve("mysql").resolve("sql").resolve("001-bootstrap.sql");
        String startScript = Files.readString(repoRoot.resolve("docs").resolve("dev-ops").resolve("app").resolve("start.ps1"));
        String dockerComposeEnvironment = Files.readString(repoRoot.resolve("docs").resolve("dev-ops").resolve("docker-compose-environment.yml"));
        String devProfile = Files.readString(backendRoot.resolve("mozhi-app").resolve("src").resolve("main").resolve("resources").resolve("application-dev.yml"));
        String testProfile = Files.readString(backendRoot.resolve("mozhi-app").resolve("src").resolve("main").resolve("resources").resolve("application-test.yml"));
        String prodProfile = Files.readString(backendRoot.resolve("mozhi-app").resolve("src").resolve("main").resolve("resources").resolve("application-prod.yml"));

        assertAll(
                () -> assertTrue(infrastructurePom.contains("<artifactId>mysql-connector-j</artifactId>"),
                        "mozhi-infrastructure must provide the MySQL driver for runtime startup"),
                () -> assertTrue(Files.exists(mysqlBootstrapSql),
                        "MySQL bootstrap SQL must exist for local Docker initialization"),
                () -> assertTrue(Files.readString(mysqlBootstrapSql).contains("CREATE DATABASE IF NOT EXISTS mozhi_test"),
                        "MySQL bootstrap SQL must create the mozhi_test database"),
                () -> assertTrue(startScript.contains("$PSScriptRoot") || startScript.contains("$MyInvocation.MyCommand.Path"),
                        "PowerShell startup script must resolve paths relative to its own directory"),
                () -> assertTrue(startScript.contains("mvnw.cmd -q -pl mozhi-app -am package"),
                        "PowerShell startup script must package the backend before launching it"),
                () -> assertTrue(startScript.contains("mozhi-app.pid"),
                        "PowerShell startup script must persist the backend PID for clean shutdown"),
                () -> assertTrue(dockerComposeEnvironment.contains("image: apache/kafka:"),
                        "Docker Compose must use a maintained Apache Kafka image"),
                () -> assertTrue(devProfile.contains("${MYSQL_HOST:127.0.0.1}") && devProfile.contains("${KAFKA_BOOTSTRAP_SERVERS:127.0.0.1:19092}"),
                        "dev profile must support env-driven middleware endpoints"),
                () -> assertTrue(devProfile.contains("allow-bypass-when-unconfigured: ${MOZHI_AUTH_CHALLENGE_ALLOW_BYPASS_WHEN_UNCONFIGURED:true}"),
                        "dev profile must allow local challenge bypass when Turnstile is unconfigured"),
                () -> assertTrue(testProfile.contains("${MYSQL_TEST_DATABASE:mozhi_test}"),
                        "test profile must support a separate test database"),
                () -> assertTrue(prodProfile.contains("allow-bypass-when-unconfigured: ${MOZHI_AUTH_CHALLENGE_ALLOW_BYPASS_WHEN_UNCONFIGURED:false}"),
                        "prod profile must keep unconfigured challenge bypass disabled by default"),
                () -> assertTrue(prodProfile.contains("${MYSQL_HOST:") && prodProfile.contains("${REDIS_HOST:") && prodProfile.contains("${KAFKA_BOOTSTRAP_SERVERS:"),
                        "prod profile must support env-driven middleware endpoints")
        );
    }
}
