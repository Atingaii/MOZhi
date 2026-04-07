package cn.zy.mozhi.domain.auth.adapter.port;

import cn.zy.mozhi.domain.auth.model.valobj.AuthTokenClaims;
import cn.zy.mozhi.domain.auth.model.valobj.AuthTokenPair;

public interface IAuthTokenPort {

    AuthTokenPair issueTokenPair(Long userId, String username, long sessionVersion);

    AuthTokenClaims parse(String token);
}
