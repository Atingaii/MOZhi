package cn.zy.mozhi.api.dto;

public record UserRegisterRequestDTO(
        String username,
        String email,
        String password,
        String nickname,
        String challengeToken
) {
}
