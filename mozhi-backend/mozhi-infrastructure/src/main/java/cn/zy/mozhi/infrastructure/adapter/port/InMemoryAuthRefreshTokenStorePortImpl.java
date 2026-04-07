package cn.zy.mozhi.infrastructure.adapter.port;

import cn.zy.mozhi.domain.auth.adapter.port.IAuthRefreshTokenStorePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnMissingBean(StringRedisTemplate.class)
public class InMemoryAuthRefreshTokenStorePortImpl implements IAuthRefreshTokenStorePort {

    private final Map<String, Instant> refreshTokenExpirations = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> userSessions = new ConcurrentHashMap<>();

    @Override
    public void store(Long userId, String tokenId, Duration ttl) {
        refreshTokenExpirations.put(buildKey(userId, tokenId), Instant.now().plus(ttl));
        userSessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(tokenId);
    }

    @Override
    public boolean isActive(Long userId, String tokenId) {
        String key = buildKey(userId, tokenId);
        Instant expiresAt = refreshTokenExpirations.get(key);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            refreshTokenExpirations.remove(key);
            return false;
        }
        return true;
    }

    @Override
    public void revoke(Long userId, String tokenId) {
        refreshTokenExpirations.remove(buildKey(userId, tokenId));
        Set<String> tokenIds = userSessions.get(userId);
        if (tokenIds == null) {
            return;
        }
        tokenIds.remove(tokenId);
        if (tokenIds.isEmpty()) {
            userSessions.remove(userId);
        }
    }

    @Override
    public void revokeAll(Long userId) {
        Set<String> tokenIds = userSessions.remove(userId);
        if (tokenIds == null) {
            return;
        }
        tokenIds.forEach(tokenId -> refreshTokenExpirations.remove(buildKey(userId, tokenId)));
    }

    private String buildKey(Long userId, String tokenId) {
        return "auth:refresh:%d:%s".formatted(userId, tokenId);
    }
}
