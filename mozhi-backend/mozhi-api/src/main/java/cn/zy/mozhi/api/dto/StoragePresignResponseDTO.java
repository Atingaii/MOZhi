package cn.zy.mozhi.api.dto;

import java.time.Instant;

public record StoragePresignResponseDTO(
        String objectKey,
        String uploadUrl,
        String publicUrl,
        String httpMethod,
        String uploadTicket,
        Instant expiresAt
) {
}
