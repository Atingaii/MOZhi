package cn.zy.mozhi.infrastructure.adapter.port;

import cn.zy.mozhi.domain.auth.adapter.port.IAuthAttemptGuardPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnMissingBean(StringRedisTemplate.class)
public class InMemoryAuthAttemptGuardPortImpl implements IAuthAttemptGuardPort {

    private final Map<String, CounterState> counters = new ConcurrentHashMap<>();
    private final Map<String, Instant> locks = new ConcurrentHashMap<>();

    @Override
    public long incrementLoginIpAttempts(String ip, Duration ttl) {
        return incrementCounter("auth:login:ip:" + ip, ttl);
    }

    @Override
    public long currentLoginIdentifierFailures(String identifier) {
        return currentCounter("auth:login:identifier:failure:" + identifier);
    }

    @Override
    public long incrementLoginIdentifierFailures(String identifier, Duration ttl) {
        return incrementCounter("auth:login:identifier:failure:" + identifier, ttl);
    }

    @Override
    public void clearLoginIdentifierFailures(String identifier) {
        counters.remove("auth:login:identifier:failure:" + identifier);
    }

    @Override
    public boolean isLoginIdentifierLocked(String identifier) {
        Instant expiresAt = locks.computeIfPresent("auth:login:identifier:lock:" + identifier, (ignored, current) ->
                current.isBefore(Instant.now()) ? null : current
        );
        return expiresAt != null;
    }

    @Override
    public void lockLoginIdentifier(String identifier, Duration ttl) {
        locks.put("auth:login:identifier:lock:" + identifier, Instant.now().plus(ttl));
    }

    @Override
    public long incrementRegisterIpAttempts(String ip, Duration ttl) {
        return incrementCounter("auth:register:ip:" + ip, ttl);
    }

    @Override
    public long incrementRegisterEmailAttempts(String email, Duration ttl) {
        return incrementCounter("auth:register:email:" + email, ttl);
    }

    @Override
    public long incrementRegisterUsernameAttempts(String username, Duration ttl) {
        return incrementCounter("auth:register:username:" + username, ttl);
    }

    @Override
    public void clearAll() {
        counters.clear();
        locks.clear();
    }

    private long incrementCounter(String key, Duration ttl) {
        Instant now = Instant.now();
        CounterState counterState = counters.compute(key, (ignored, current) -> {
            if (current == null || current.expiresAt().isBefore(now)) {
                return new CounterState(1L, now.plus(ttl));
            }
            return new CounterState(current.count() + 1, current.expiresAt());
        });
        return counterState.count();
    }

    private long currentCounter(String key) {
        CounterState counterState = counters.computeIfPresent(key, (ignored, current) ->
                current.expiresAt().isBefore(Instant.now()) ? null : current
        );
        return counterState == null ? 0L : counterState.count();
    }

    private record CounterState(long count, Instant expiresAt) {
    }
}
