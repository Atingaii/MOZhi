package cn.zy.mozhi.domain.auth.service;

import cn.zy.mozhi.domain.auth.adapter.port.IAuthAttemptGuardPort;
import cn.zy.mozhi.domain.auth.adapter.port.IAuthAuditPort;
import cn.zy.mozhi.domain.auth.adapter.port.IAuthChallengeVerifierPort;
import cn.zy.mozhi.domain.auth.model.valobj.AuthRequestContext;
import cn.zy.mozhi.types.enums.ResponseCode;
import cn.zy.mozhi.types.exception.BaseException;

import java.time.Duration;
import java.util.Locale;

public class AuthSecurityPolicyService {

    private final IAuthAttemptGuardPort authAttemptGuardPort;
    private final IAuthChallengeVerifierPort authChallengeVerifierPort;
    private final IAuthAuditPort authAuditPort;
    private final int loginIpMaxAttempts;
    private final Duration loginIpWindow;
    private final int loginIdentifierChallengeThreshold;
    private final int loginIdentifierLockThreshold;
    private final Duration loginIdentifierWindow;
    private final Duration loginIdentifierLockWindow;
    private final int registerIpMaxAttempts;
    private final int registerIpChallengeThreshold;
    private final Duration registerIpWindow;
    private final int registerEmailMaxAttempts;
    private final Duration registerEmailWindow;
    private final int registerUsernameMaxAttempts;
    private final Duration registerUsernameWindow;

    public AuthSecurityPolicyService(IAuthAttemptGuardPort authAttemptGuardPort,
                                     IAuthChallengeVerifierPort authChallengeVerifierPort,
                                     IAuthAuditPort authAuditPort,
                                     int loginIpMaxAttempts,
                                     Duration loginIpWindow,
                                     int loginIdentifierChallengeThreshold,
                                     int loginIdentifierLockThreshold,
                                     Duration loginIdentifierWindow,
                                     Duration loginIdentifierLockWindow,
                                     int registerIpMaxAttempts,
                                     int registerIpChallengeThreshold,
                                     Duration registerIpWindow,
                                     int registerEmailMaxAttempts,
                                     Duration registerEmailWindow,
                                     int registerUsernameMaxAttempts,
                                     Duration registerUsernameWindow) {
        this.authAttemptGuardPort = authAttemptGuardPort;
        this.authChallengeVerifierPort = authChallengeVerifierPort;
        this.authAuditPort = authAuditPort;
        this.loginIpMaxAttempts = loginIpMaxAttempts;
        this.loginIpWindow = loginIpWindow;
        this.loginIdentifierChallengeThreshold = loginIdentifierChallengeThreshold;
        this.loginIdentifierLockThreshold = loginIdentifierLockThreshold;
        this.loginIdentifierWindow = loginIdentifierWindow;
        this.loginIdentifierLockWindow = loginIdentifierLockWindow;
        this.registerIpMaxAttempts = registerIpMaxAttempts;
        this.registerIpChallengeThreshold = registerIpChallengeThreshold;
        this.registerIpWindow = registerIpWindow;
        this.registerEmailMaxAttempts = registerEmailMaxAttempts;
        this.registerEmailWindow = registerEmailWindow;
        this.registerUsernameMaxAttempts = registerUsernameMaxAttempts;
        this.registerUsernameWindow = registerUsernameWindow;
    }

    public void assertLoginAllowed(String identifier, String challengeToken, AuthRequestContext requestContext) {
        String normalizedIp = normalize(requestContext.ip());
        String normalizedIdentifier = normalize(identifier);

        long loginIpAttempts = authAttemptGuardPort.incrementLoginIpAttempts(normalizedIp, loginIpWindow);
        if (loginIpAttempts > loginIpMaxAttempts) {
            authAuditPort.record("login_rate_limited", requestContext, auditSubject(identifier), "ip_limit");
            throw new BaseException(ResponseCode.TOO_MANY_REQUESTS, "try again later");
        }

        if (authAttemptGuardPort.isLoginIdentifierLocked(normalizedIdentifier)) {
            authAuditPort.record("login_locked", requestContext, auditSubject(identifier), "identifier_locked");
            throw new BaseException(ResponseCode.TOO_MANY_REQUESTS, "try again later");
        }

        long failureCount = authAttemptGuardPort.currentLoginIdentifierFailures(normalizedIdentifier);
        if (failureCount >= loginIdentifierChallengeThreshold
                && !authChallengeVerifierPort.verify(challengeToken, requestContext)) {
            authAuditPort.record("login_challenge_required", requestContext, auditSubject(identifier), "challenge_missing");
            throw new BaseException(ResponseCode.AUTH_CHALLENGE_REQUIRED, "challenge required");
        }
    }

    public void recordLoginFailure(String identifier, AuthRequestContext requestContext) {
        String normalizedIdentifier = normalize(identifier);
        long failureCount = authAttemptGuardPort.incrementLoginIdentifierFailures(normalizedIdentifier, loginIdentifierWindow);
        authAuditPort.record("login_failed", requestContext, auditSubject(identifier), "invalid_credentials");
        if (failureCount >= loginIdentifierLockThreshold) {
            authAttemptGuardPort.lockLoginIdentifier(normalizedIdentifier, loginIdentifierLockWindow);
            authAuditPort.record("login_locked", requestContext, auditSubject(identifier), "failure_threshold");
        }
    }

    public void recordLoginSuccess(String identifier, Long userId, AuthRequestContext requestContext) {
        authAttemptGuardPort.clearLoginIdentifierFailures(normalize(identifier));
        authAuditPort.record("login_succeeded", requestContext, auditSubject(identifier), "user:" + userId);
    }

    public void assertRegisterAllowed(String username,
                                      String email,
                                      String challengeToken,
                                      AuthRequestContext requestContext) {
        long registerIpAttempts = authAttemptGuardPort.incrementRegisterIpAttempts(normalize(requestContext.ip()), registerIpWindow);
        if (registerIpAttempts > registerIpMaxAttempts) {
            authAuditPort.record("register_rate_limited", requestContext, auditSubject(email), "ip_limit");
            throw new BaseException(ResponseCode.TOO_MANY_REQUESTS, "try again later");
        }

        if (registerIpAttempts > registerIpChallengeThreshold
                && !authChallengeVerifierPort.verify(challengeToken, requestContext)) {
            authAuditPort.record("register_challenge_required", requestContext, auditSubject(email), "challenge_missing");
            throw new BaseException(ResponseCode.AUTH_CHALLENGE_REQUIRED, "challenge required");
        }

        long registerUsernameAttempts = authAttemptGuardPort.incrementRegisterUsernameAttempts(normalize(username), registerUsernameWindow);
        if (registerUsernameAttempts > registerUsernameMaxAttempts) {
            authAuditPort.record("register_rate_limited", requestContext, auditSubject(username), "username_limit");
            throw new BaseException(ResponseCode.TOO_MANY_REQUESTS, "try again later");
        }

        long registerEmailAttempts = authAttemptGuardPort.incrementRegisterEmailAttempts(normalize(email), registerEmailWindow);
        if (registerEmailAttempts > registerEmailMaxAttempts) {
            authAuditPort.record("register_rate_limited", requestContext, auditSubject(email), "email_limit");
            throw new BaseException(ResponseCode.TOO_MANY_REQUESTS, "try again later");
        }
    }

    public void recordRegisterSuccess(String username, Long userId, AuthRequestContext requestContext) {
        authAuditPort.record("register_succeeded", requestContext, auditSubject(username), "user:" + userId);
    }

    public void recordRegisterFailure(String username, String email, AuthRequestContext requestContext, String reason) {
        authAuditPort.record("register_failed", requestContext, auditSubject(email != null ? email : username), reason);
    }

    public void recordRefreshSuccess(Long userId, AuthRequestContext requestContext) {
        authAuditPort.record("refresh_succeeded", requestContext, "user:" + userId, "ok");
    }

    public void recordRefreshFailure(AuthRequestContext requestContext, String reason) {
        authAuditPort.record("refresh_failed", requestContext, "anonymous", reason);
    }

    public void recordLogout(Long userId, AuthRequestContext requestContext) {
        authAuditPort.record("logout_succeeded", requestContext, "user:" + userId, "ok");
    }

    public void recordLogoutAll(Long userId, AuthRequestContext requestContext) {
        authAuditPort.record("logout_all_succeeded", requestContext, "user:" + userId, "ok");
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String auditSubject(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return "anonymous";
        }
        if (normalized.contains("@")) {
            int atIndex = normalized.indexOf('@');
            String localPart = normalized.substring(0, atIndex);
            String domain = normalized.substring(atIndex);
            String prefix = localPart.isEmpty() ? "*" : localPart.substring(0, 1);
            return prefix + "***" + domain;
        }
        if (normalized.length() == 1) {
            return normalized + "***";
        }
        return normalized.substring(0, 2) + "***";
    }
}
