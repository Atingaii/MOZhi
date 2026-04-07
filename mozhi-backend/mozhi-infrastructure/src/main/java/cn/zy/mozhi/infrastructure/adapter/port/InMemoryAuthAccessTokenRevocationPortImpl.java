package cn.zy.mozhi.infrastructure.adapter.port;

import cn.zy.mozhi.domain.auth.adapter.port.IAuthAccessTokenRevocationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnMissingBean(StringRedisTemplate.class)
public class InMemoryAuthAccessTokenRevocationPortImpl implements IAuthAccessTokenRevocationPort {

    private final Map<String, Instant> blacklistedTokens = new ConcurrentHashMap<>();
    private final Map<Long, Long> sessionVersions = new ConcurrentHashMap<>();

    @Override
    public void blacklist(Long userId, String tokenId, Duration ttl) {
        blacklistedTokens.put(buildBlacklistKey(userId, tokenId), Instant.now().plus(ttl));
    }

    @Override
    public boolean isBlacklisted(Long userId, String tokenId) {
        String key = buildBlacklistKey(userId, tokenId);
        Instant expiresAt = blacklistedTokens.get(key);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            blacklistedTokens.remove(key);
            return false;
        }
        return true;
    }

    @Override
    public long currentSessionVersion(Long userId) {
        return sessionVersions.getOrDefault(userId, 0L);
    }

    @Override
    public long bumpSessionVersion(Long userId) {
        return sessionVersions.merge(userId, 1L, Long::sum);
    }

    private String buildBlacklistKey(Long userId, String tokenId) {
        return "auth:access:blacklist:%d:%s".formatted(userId, tokenId);
    }
}
