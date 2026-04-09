package cn.zy.mozhi.api.dto;

import java.time.LocalDateTime;

public record DraftDetailDTO(
        Long draftId,
        Long authorId,
        String title,
        String content,
        String status,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
