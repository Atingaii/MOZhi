package cn.zy.mozhi.api.dto;

public record UserProfileDTO(
        Long userId,
        String username,
        String email,
        String nickname,
        String avatarUrl,
        String bio,
        String status
) {
}
