package cn.zy.mozhi.infrastructure.adapter.port;

import cn.zy.mozhi.domain.auth.adapter.port.IAuthAccessTokenRevocationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisAuthAccessTokenRevocationPortImpl implements IAuthAccessTokenRevocationPort {

    private final StringRedisTemplate stringRedisTemplate;
    private final Duration accessTokenTtl;

    public RedisAuthAccessTokenRevocationPortImpl(
            StringRedisTemplate stringRedisTemplate,
            @Value("${mozhi.auth.token.access-token-ttl:PT15M}") Duration accessTokenTtl
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.accessTokenTtl = accessTokenTtl;
    }

    @Override
    public void blacklist(Long userId, String tokenId, Duration ttl) {
        stringRedisTemplate.opsForValue().set(buildBlacklistKey(userId, tokenId), "1", ttl);
    }

    @Override
    public boolean isBlacklisted(Long userId, String tokenId) {
        Boolean hasKey = stringRedisTemplate.hasKey(buildBlacklistKey(userId, tokenId));
        return Boolean.TRUE.equals(hasKey);
    }

    @Override
    public long currentSessionVersion(Long userId) {
        String value = stringRedisTemplate.opsForValue().get(buildSessionVersionKey(userId));
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return Long.parseLong(value);
    }

    @Override
    public long bumpSessionVersion(Long userId) {
        Long value = stringRedisTemplate.opsForValue().increment(buildSessionVersionKey(userId));
        if (value == null) {
            return 0L;
        }
        stringRedisTemplate.expire(buildSessionVersionKey(userId), accessTokenTtl.multipliedBy(2));
        return value;
    }

    private String buildBlacklistKey(Long userId, String tokenId) {
        return "auth:access:blacklist:%d:%s".formatted(userId, tokenId);
    }

    private String buildSessionVersionKey(Long userId) {
        return "auth:access:session-version:%d".formatted(userId);
    }
}
