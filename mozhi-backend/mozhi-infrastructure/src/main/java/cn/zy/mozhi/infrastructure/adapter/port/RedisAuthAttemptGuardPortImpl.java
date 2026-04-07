package cn.zy.mozhi.infrastructure.adapter.port;

import cn.zy.mozhi.domain.auth.adapter.port.IAuthAttemptGuardPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisAuthAttemptGuardPortImpl implements IAuthAttemptGuardPort {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisAuthAttemptGuardPortImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

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
        stringRedisTemplate.delete("auth:login:identifier:failure:" + identifier);
    }

    @Override
    public boolean isLoginIdentifierLocked(String identifier) {
        Boolean hasKey = stringRedisTemplate.hasKey("auth:login:identifier:lock:" + identifier);
        return Boolean.TRUE.equals(hasKey);
    }

    @Override
    public void lockLoginIdentifier(String identifier, Duration ttl) {
        stringRedisTemplate.opsForValue().set("auth:login:identifier:lock:" + identifier, "1", ttl);
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
        Set<String> keys = stringRedisTemplate.keys("auth:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    private long incrementCounter(String key, Duration ttl) {
        Long value = stringRedisTemplate.opsForValue().increment(key);
        if (value == null) {
            return 0L;
        }
        stringRedisTemplate.expire(key, ttl);
        return value;
    }

    private long currentCounter(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return Long.parseLong(value);
    }
}
