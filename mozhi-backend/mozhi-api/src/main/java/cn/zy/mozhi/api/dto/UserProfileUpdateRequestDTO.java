package cn.zy.mozhi.api.dto;

public record UserProfileUpdateRequestDTO(
        String nickname,
        String bio
) {
}
