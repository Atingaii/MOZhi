package cn.zy.mozhi.api.dto;

import java.time.Instant;

public record AuthTokenResponseDTO(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt
) {
}
