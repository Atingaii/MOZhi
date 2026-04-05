package cn.zy.mozhi.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendBootstrapVerifierTest {

    @Test
    void should_keep_backend_bootstrap_contract() {
        Path moduleRoot = Path.of("").toAbsolutePath();
        Path backendRoot = moduleRoot.getParent();
        Path repoRoot = backendRoot.getParent();

        assertTrue(Files.exists(backendRoot.resolve("pom.xml")));

        List<String> modules = List.of(
                "mozhi-types",
                "mozhi-api",
                "mozhi-domain",
                "mozhi-infrastructure",
                "mozhi-trigger",
                "mozhi-app"
        );

        for (String module : modules) {
            assertTrue(Files.exists(backendRoot.resolve(module).resolve("pom.xml")));
        }

        assertTrue(Files.exists(backendRoot.resolve("mvnw.cmd")));
        assertTrue(Files.exists(backendRoot.resolve(".mvn").resolve("wrapper").resolve("maven-wrapper.properties")));
    }
}
