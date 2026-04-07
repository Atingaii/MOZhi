package cn.zy.mozhi.domain.storage.model.valobj;

import java.time.Instant;

public record StoragePresignedUpload(
        String objectKey,
        String uploadUrl,
        String publicUrl,
        String httpMethod,
        Instant expiresAt
) {
}
