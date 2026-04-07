package cn.zy.mozhi.infrastructure.adapter.port;

import cn.zy.mozhi.domain.auth.adapter.port.IAuthRefreshTokenStorePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisAuthRefreshTokenStorePortImpl implements IAuthRefreshTokenStorePort {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisAuthRefreshTokenStorePortImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void store(Long userId, String tokenId, Duration ttl) {
        stringRedisTemplate.opsForValue().set(buildKey(userId, tokenId), "1", ttl);
        stringRedisTemplate.opsForSet().add(buildSessionKey(userId), tokenId);
        stringRedisTemplate.expire(buildSessionKey(userId), ttl);
    }

    @Override
    public boolean isActive(Long userId, String tokenId) {
        Boolean hasKey = stringRedisTemplate.hasKey(buildKey(userId, tokenId));
        return Boolean.TRUE.equals(hasKey);
    }

    @Override
    public void revoke(Long userId, String tokenId) {
        stringRedisTemplate.delete(buildKey(userId, tokenId));
        stringRedisTemplate.opsForSet().remove(buildSessionKey(userId), tokenId);
    }

    @Override
    public void revokeAll(Long userId) {
        String sessionKey = buildSessionKey(userId);
        var tokenIds = stringRedisTemplate.opsForSet().members(sessionKey);
        if (tokenIds != null && !tokenIds.isEmpty()) {
            tokenIds.forEach(tokenId -> stringRedisTemplate.delete(buildKey(userId, tokenId)));
        }
        stringRedisTemplate.delete(sessionKey);
    }

    private String buildKey(Long userId, String tokenId) {
        return "auth:refresh:%d:%s".formatted(userId, tokenId);
    }

    private String buildSessionKey(Long userId) {
        return "auth:session:%d".formatted(userId);
    }
}
