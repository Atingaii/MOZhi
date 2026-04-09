package cn.zy.mozhi.api.dto;

import java.time.LocalDateTime;

public record DraftSummaryDTO(
        Long draftId,
        String title,
        String status,
        LocalDateTime updatedAt
) {
}
