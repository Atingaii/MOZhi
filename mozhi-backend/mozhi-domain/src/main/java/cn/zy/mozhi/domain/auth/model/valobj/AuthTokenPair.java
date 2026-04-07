package cn.zy.mozhi.domain.auth.model.valobj;

import java.time.Instant;

public record AuthTokenPair(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        String refreshTokenId,
        Instant refreshTokenExpiresAt
) {
}
