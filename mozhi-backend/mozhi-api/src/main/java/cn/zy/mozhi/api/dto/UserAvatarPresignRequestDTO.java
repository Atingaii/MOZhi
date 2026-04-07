package cn.zy.mozhi.api.dto;

public record UserAvatarPresignRequestDTO(
        String fileName,
        String contentType
) {
}
