package cn.zy.mozhi.domain.auth.model.valobj;

import java.time.Instant;

public record AuthTokenClaims(
        Long userId,
        String username,
        String tokenId,
        AuthTokenType tokenType,
        long sessionVersion,
        Instant issuedAt,
        Instant expiresAt
) {
}
