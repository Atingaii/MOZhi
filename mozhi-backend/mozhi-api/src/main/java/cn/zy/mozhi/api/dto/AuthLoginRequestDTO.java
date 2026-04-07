package cn.zy.mozhi.api.dto;

public record AuthLoginRequestDTO(
        String identifier,
        String password,
        String challengeToken
) {
}
