package cn.zy.mozhi.api.dto;

import java.time.LocalDateTime;

public record DraftMediaDTO(
        Long mediaRefId,
        String objectKey,
        String publicUrl,
        String mediaType,
        String contentType,
        Long sizeBytes,
        String uploadStatus,
        Integer sortOrder,
        LocalDateTime boundAt
) {
}
