package cn.zy.mozhi.api.dto;

import java.time.Instant;

public record HealthStatusDTO(
        String application,
        String status,
        String profile,
        Instant checkedAt,
        String documentationUrl
) {
}
