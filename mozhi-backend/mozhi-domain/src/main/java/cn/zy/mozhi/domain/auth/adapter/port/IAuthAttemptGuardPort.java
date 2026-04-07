package cn.zy.mozhi.domain.auth.adapter.port;

import java.time.Duration;

public interface IAuthAttemptGuardPort {

    long incrementLoginIpAttempts(String ip, Duration ttl);

    long currentLoginIdentifierFailures(String identifier);

    long incrementLoginIdentifierFailures(String identifier, Duration ttl);

    void clearLoginIdentifierFailures(String identifier);

    boolean isLoginIdentifierLocked(String identifier);

    void lockLoginIdentifier(String identifier, Duration ttl);

    long incrementRegisterIpAttempts(String ip, Duration ttl);

    long incrementRegisterEmailAttempts(String email, Duration ttl);

    long incrementRegisterUsernameAttempts(String username, Duration ttl);

    void clearAll();
}
