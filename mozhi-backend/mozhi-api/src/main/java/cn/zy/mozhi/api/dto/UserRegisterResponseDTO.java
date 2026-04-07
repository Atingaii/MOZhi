package cn.zy.mozhi.api.dto;

public record UserRegisterResponseDTO(
        Long userId,
        String username,
        String nickname
) {
}
