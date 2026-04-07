package cn.zy.mozhi.infrastructure.adapter.port;

import cn.zy.mozhi.domain.auth.adapter.port.IAuthTokenPort;
import cn.zy.mozhi.domain.auth.model.valobj.AuthTokenClaims;
import cn.zy.mozhi.domain.auth.model.valobj.AuthTokenPair;
import cn.zy.mozhi.domain.auth.model.valobj.AuthTokenType;
import cn.zy.mozhi.types.enums.ResponseCode;
import cn.zy.mozhi.types.exception.BaseException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class JwtAuthTokenPortImpl implements IAuthTokenPort {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtAuthTokenPortImpl(
            @Value("${mozhi.auth.token.issuer:mozhi-app}") String issuer,
            @Value("${mozhi.auth.token.access-token-ttl:PT15M}") Duration accessTokenTtl,
            @Value("${mozhi.auth.token.refresh-token-ttl:P7D}") Duration refreshTokenTtl
    ) {
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;

        KeyPair keyPair = generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();

        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
        this.jwtDecoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    @Override
    public AuthTokenPair issueTokenPair(Long userId, String username, long sessionVersion) {
        Instant now = Instant.now();
        Instant accessExpiresAt = now.plus(accessTokenTtl);
        Instant refreshExpiresAt = now.plus(refreshTokenTtl);
        String accessTokenId = UUID.randomUUID().toString();
        String refreshTokenId = UUID.randomUUID().toString();

        String accessToken = encodeToken(userId, username, accessTokenId, AuthTokenType.ACCESS, sessionVersion, now, accessExpiresAt);
        String refreshToken = encodeToken(userId, username, refreshTokenId, AuthTokenType.REFRESH, sessionVersion, now, refreshExpiresAt);

        return new AuthTokenPair(
                accessToken,
                accessExpiresAt,
                refreshToken,
                refreshTokenId,
                refreshExpiresAt
        );
    }

    @Override
    public AuthTokenClaims parse(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            Number userIdClaim = jwt.getClaim("user_id");
            String username = jwt.getClaim("username");
            String tokenType = jwt.getClaim("token_type");
            Number sessionVersionClaim = jwt.getClaim("session_version");

            if (userIdClaim == null || username == null || tokenType == null || sessionVersionClaim == null || jwt.getId() == null) {
                throw invalidToken();
            }
            if (jwt.getIssuedAt() == null || jwt.getExpiresAt() == null) {
                throw invalidToken();
            }

            return new AuthTokenClaims(
                    userIdClaim.longValue(),
                    username,
                    jwt.getId(),
                    AuthTokenType.valueOf(tokenType),
                    sessionVersionClaim.longValue(),
                    jwt.getIssuedAt(),
                    jwt.getExpiresAt()
            );
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidToken();
        }
    }

    private String encodeToken(Long userId,
                               String username,
                               String tokenId,
                               AuthTokenType tokenType,
                               long sessionVersion,
                               Instant issuedAt,
                               Instant expiresAt) {
        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(String.valueOf(userId))
                .id(tokenId)
                .claim("user_id", userId)
                .claim("username", username)
                .claim("token_type", tokenType.name())
                .claim("session_version", sessionVersion)
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(
                        org.springframework.security.oauth2.jwt.JwsHeader.with(SignatureAlgorithm.RS256).build(),
                        claimsSet
                )
        ).getTokenValue();
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("rsa key pair generation failed", exception);
        }
    }

    private BaseException invalidToken() {
        return new BaseException(ResponseCode.UNAUTHORIZED, "token is invalid");
    }
}
