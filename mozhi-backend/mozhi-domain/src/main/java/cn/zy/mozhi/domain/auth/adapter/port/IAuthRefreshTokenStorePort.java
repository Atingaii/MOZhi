package cn.zy.mozhi.domain.auth.adapter.port;

import java.time.Duration;

public interface IAuthRefreshTokenStorePort {

    void store(Long userId, String tokenId, Duration ttl);

    boolean isActive(Long userId, String tokenId);

    void revoke(Long userId, String tokenId);

    void revokeAll(Long userId);
}
