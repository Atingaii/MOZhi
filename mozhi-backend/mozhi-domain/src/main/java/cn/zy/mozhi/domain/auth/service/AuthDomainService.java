package cn.zy.mozhi.domain.auth.service;

import cn.zy.mozhi.domain.auth.adapter.port.IAuthAccessTokenRevocationPort;
import cn.zy.mozhi.domain.auth.adapter.port.IAuthRefreshTokenStorePort;
import cn.zy.mozhi.domain.auth.adapter.port.IAuthTokenPort;
import cn.zy.mozhi.domain.auth.model.valobj.AuthTokenClaims;
import cn.zy.mozhi.domain.auth.model.valobj.AuthRequestContext;
import cn.zy.mozhi.domain.auth.model.valobj.AuthTokenPair;
import cn.zy.mozhi.domain.auth.model.valobj.AuthTokenType;
import cn.zy.mozhi.domain.user.adapter.port.IUserPasswordEncoderPort;
import cn.zy.mozhi.domain.user.adapter.repository.IUserRepository;
import cn.zy.mozhi.domain.user.model.entity.UserEntity;
import cn.zy.mozhi.types.enums.ResponseCode;
import cn.zy.mozhi.types.exception.BaseException;

import java.time.Duration;
import java.time.Instant;

public class AuthDomainService {

    private final IUserRepository userRepository;
    private final IUserPasswordEncoderPort userPasswordEncoderPort;
    private final IAuthTokenPort authTokenPort;
    private final IAuthRefreshTokenStorePort authRefreshTokenStorePort;
    private final IAuthAccessTokenRevocationPort authAccessTokenRevocationPort;
    private final AuthSecurityPolicyService authSecurityPolicyService;

    public AuthDomainService(IUserRepository userRepository,
                             IUserPasswordEncoderPort userPasswordEncoderPort,
                             IAuthTokenPort authTokenPort,
                             IAuthRefreshTokenStorePort authRefreshTokenStorePort,
                             IAuthAccessTokenRevocationPort authAccessTokenRevocationPort,
                             AuthSecurityPolicyService authSecurityPolicyService) {
        this.userRepository = userRepository;
        this.userPasswordEncoderPort = userPasswordEncoderPort;
        this.authTokenPort = authTokenPort;
        this.authRefreshTokenStorePort = authRefreshTokenStorePort;
        this.authAccessTokenRevocationPort = authAccessTokenRevocationPort;
        this.authSecurityPolicyService = authSecurityPolicyService;
    }

    public AuthTokenPair login(String identifier, String rawPassword, String challengeToken, AuthRequestContext requestContext) {
        String normalizedIdentifier = requireText(identifier, "identifier must not be blank");
        String normalizedPassword = requireText(rawPassword, "password must not be blank");
        authSecurityPolicyService.assertLoginAllowed(normalizedIdentifier, challengeToken, requestContext);
        UserEntity userEntity = userRepository.findByUsername(normalizedIdentifier)
                .or(() -> userRepository.findByEmail(normalizedIdentifier))
                .orElse(null);

        if (userEntity == null || !userPasswordEncoderPort.matches(normalizedPassword, userEntity.getPasswordHash())) {
            authSecurityPolicyService.recordLoginFailure(normalizedIdentifier, requestContext);
            throw invalidCredentials();
        }

        authSecurityPolicyService.recordLoginSuccess(userEntity.getUsername(), userEntity.getId(), requestContext);
        return issueAndStoreTokens(userEntity);
    }

    public AuthTokenPair refresh(String refreshToken, AuthRequestContext requestContext) {
        try {
            AuthTokenClaims tokenClaims = parseRefreshToken(refreshToken);
            if (!authRefreshTokenStorePort.isActive(tokenClaims.userId(), tokenClaims.tokenId())) {
                throw invalidRefreshToken();
            }

            UserEntity userEntity = userRepository.findById(tokenClaims.userId())
                    .orElseThrow(this::invalidRefreshToken);

            authRefreshTokenStorePort.revoke(tokenClaims.userId(), tokenClaims.tokenId());
            AuthTokenPair tokenPair = issueAndStoreTokens(userEntity);
            authSecurityPolicyService.recordRefreshSuccess(userEntity.getId(), requestContext);
            return tokenPair;
        } catch (BaseException exception) {
            authSecurityPolicyService.recordRefreshFailure(requestContext, exception.getErrorCode());
            throw exception;
        }
    }

    public void logout(String accessToken, String refreshToken, AuthRequestContext requestContext) {
        AuthTokenClaims accessTokenClaims = authenticateAccessToken(accessToken);
        AuthTokenClaims refreshTokenClaims = parseRefreshToken(refreshToken);

        if (!accessTokenClaims.userId().equals(refreshTokenClaims.userId())) {
            throw invalidRefreshToken();
        }

        authRefreshTokenStorePort.revoke(refreshTokenClaims.userId(), refreshTokenClaims.tokenId());
        authAccessTokenRevocationPort.blacklist(
                accessTokenClaims.userId(),
                accessTokenClaims.tokenId(),
                resolveTtl(accessTokenClaims.expiresAt())
        );
        authSecurityPolicyService.recordLogout(accessTokenClaims.userId(), requestContext);
    }

    public void logoutAll(String accessToken, AuthRequestContext requestContext) {
        AuthTokenClaims accessTokenClaims = authenticateAccessToken(accessToken);
        authRefreshTokenStorePort.revokeAll(accessTokenClaims.userId());
        authAccessTokenRevocationPort.bumpSessionVersion(accessTokenClaims.userId());
        authSecurityPolicyService.recordLogoutAll(accessTokenClaims.userId(), requestContext);
    }

    public AuthTokenClaims authenticateAccessToken(String accessToken) {
        AuthTokenClaims tokenClaims = parseAccessToken(accessToken);
        if (authAccessTokenRevocationPort.isBlacklisted(tokenClaims.userId(), tokenClaims.tokenId())) {
            throw invalidAccessToken();
        }

        if (tokenClaims.sessionVersion() != authAccessTokenRevocationPort.currentSessionVersion(tokenClaims.userId())) {
            throw invalidAccessToken();
        }

        return tokenClaims;
    }

    private AuthTokenPair issueAndStoreTokens(UserEntity userEntity) {
        long sessionVersion = authAccessTokenRevocationPort.currentSessionVersion(userEntity.getId());
        AuthTokenPair tokenPair = authTokenPort.issueTokenPair(userEntity.getId(), userEntity.getUsername(), sessionVersion);
        authRefreshTokenStorePort.store(
                userEntity.getId(),
                tokenPair.refreshTokenId(),
                resolveTtl(tokenPair.refreshTokenExpiresAt())
        );
        return tokenPair;
    }

    private Duration resolveTtl(Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        return ttl.isNegative() ? Duration.ZERO : ttl;
    }

    private AuthTokenClaims parseAccessToken(String accessToken) {
        AuthTokenClaims tokenClaims = authTokenPort.parse(requireText(accessToken, "accessToken must not be blank"));
        if (tokenClaims.tokenType() != AuthTokenType.ACCESS) {
            throw invalidAccessToken();
        }
        return tokenClaims;
    }

    private AuthTokenClaims parseRefreshToken(String refreshToken) {
        AuthTokenClaims tokenClaims = authTokenPort.parse(requireText(refreshToken, "refreshToken must not be blank"));
        if (tokenClaims.tokenType() != AuthTokenType.REFRESH) {
            throw invalidRefreshToken();
        }
        return tokenClaims;
    }

    private BaseException invalidCredentials() {
        return new BaseException(ResponseCode.UNAUTHORIZED, "identifier or password is invalid");
    }

    private BaseException invalidAccessToken() {
        return new BaseException(ResponseCode.UNAUTHORIZED, "access token is invalid");
    }

    private BaseException invalidRefreshToken() {
        return new BaseException(ResponseCode.UNAUTHORIZED, "refresh token is invalid");
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BaseException(ResponseCode.BAD_REQUEST, message);
        }
        return value.trim();
    }
}
