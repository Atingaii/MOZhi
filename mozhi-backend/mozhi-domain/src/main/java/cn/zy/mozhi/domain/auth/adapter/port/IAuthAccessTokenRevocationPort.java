package cn.zy.mozhi.domain.auth.adapter.port;

import java.time.Duration;

public interface IAuthAccessTokenRevocationPort {

    void blacklist(Long userId, String tokenId, Duration ttl);

    boolean isBlacklisted(Long userId, String tokenId);

    long currentSessionVersion(Long userId);

    long bumpSessionVersion(Long userId);
}
