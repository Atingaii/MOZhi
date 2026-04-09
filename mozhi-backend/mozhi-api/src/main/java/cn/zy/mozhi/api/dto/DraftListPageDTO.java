package cn.zy.mozhi.api.dto;

import java.util.List;

public record DraftListPageDTO(
        int page,
        int pageSize,
        long total,
        List<DraftSummaryDTO> items
) {
}
