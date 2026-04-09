package cn.zy.mozhi.api.dto;

public record DraftStatusTransitionRequestDTO(
        String targetStatus,
        Long expectedVersion
) {
}
