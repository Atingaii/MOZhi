package cn.zy.mozhi.trigger.http;

import cn.zy.mozhi.api.dto.ApiResponse;
import cn.zy.mozhi.api.dto.HealthStatusDTO;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;

@RestController
@RequestMapping("/api")
public class HealthController {

    private static final String DEFAULT_APPLICATION_NAME = "mozhi-app";
    private static final String HEALTHY_STATUS = "UP";

    private final Environment environment;

    public HealthController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/health")
    public ApiResponse<HealthStatusDTO> health() {
        String application = environment.getProperty("spring.application.name", DEFAULT_APPLICATION_NAME);
        String profile = Arrays.stream(environment.getActiveProfiles())
                .findFirst()
                .orElse("default");

        HealthStatusDTO payload = new HealthStatusDTO(
                application,
                HEALTHY_STATUS,
                profile,
                Instant.now(),
                "/swagger-ui/index.html"
        );
        return ApiResponse.success(payload);
    }
}
