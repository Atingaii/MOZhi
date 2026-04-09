package cn.zy.mozhi.api.dto;

public record DraftUpdateRequestDTO(
        String title,
        String content,
        Long expectedVersion
) {
}
