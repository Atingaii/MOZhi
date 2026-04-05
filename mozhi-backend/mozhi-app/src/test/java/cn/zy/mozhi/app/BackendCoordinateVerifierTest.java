package cn.zy.mozhi.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendCoordinateVerifierTest {

    @Test
    void should_align_backend_coordinates_with_cn_zy_root_package() throws IOException {
        Path moduleRoot = Path.of("").toAbsolutePath();
        Path backendRoot = moduleRoot.getParent();
        Path repoRoot = backendRoot.getParent();

        List<Path> pomFiles = List.of(
                backendRoot.resolve("pom.xml"),
                backendRoot.resolve("mozhi-types").resolve("pom.xml"),
                backendRoot.resolve("mozhi-api").resolve("pom.xml"),
                backendRoot.resolve("mozhi-domain").resolve("pom.xml"),
                backendRoot.resolve("mozhi-infrastructure").resolve("pom.xml"),
                backendRoot.resolve("mozhi-trigger").resolve("pom.xml"),
                backendRoot.resolve("mozhi-app").resolve("pom.xml")
        );

        String parentPom = Files.readString(backendRoot.resolve("pom.xml"));
        Path repoPom = repoRoot.resolve("pom.xml");
        Path mavenProjectsManager = repoRoot.resolve(".idea").resolve("MavenProjectsManager.xml");
        Path applicationPath = backendRoot.resolve("mozhi-app").resolve("src").resolve("main").resolve("java")
                .resolve("cn").resolve("zy").resolve("mozhi").resolve("app").resolve("Application.java");

        assertAll(
                () -> assertTrue(Files.exists(repoPom),
                        "repo root must contain a pom.xml so IDEA can import the workspace as a Maven project"),
                () -> assertTrue(Files.exists(mavenProjectsManager),
                        "IDEA metadata must include MavenProjectsManager.xml for automatic Maven import"),
                () -> assertTrue(parentPom.contains("<groupId>cn.zy</groupId>"),
                        "backend parent pom must use cn.zy as the groupId"),
                () -> assertFalse(parentPom.contains("<groupId>${project.groupId}</groupId>"),
                        "backend parent pom must use explicit internal module coordinates"),
                () -> assertTrue(Files.exists(applicationPath),
                        "Application.java must live under the cn/zy source path so the IDE can recognise it"),
                () -> assertTrue(Files.readString(applicationPath).contains("package cn.zy.mozhi.app;"),
                        "Application.java must declare the cn.zy.mozhi.app package"),
                () -> assertTrue(Files.readString(applicationPath).contains("@SpringBootApplication(scanBasePackages = \"cn.zy.mozhi\")"),
                        "Application.java must scan the cn.zy.mozhi package tree"),
                () -> {
                    for (Path pomFile : pomFiles) {
                        assertFalse(Files.readString(pomFile).contains("cn.bugstack"),
                                pomFile + " must not reference the old cn.bugstack groupId");
                    }
                }
        );
    }
}
