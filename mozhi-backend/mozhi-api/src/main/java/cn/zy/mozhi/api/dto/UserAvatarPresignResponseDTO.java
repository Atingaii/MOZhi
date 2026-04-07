package cn.zy.mozhi.api.dto;

import java.time.Instant;

public record UserAvatarPresignResponseDTO(
        String objectKey,
        String uploadUrl,
        String publicUrl,
        String httpMethod,
        Instant expiresAt
) {
}
