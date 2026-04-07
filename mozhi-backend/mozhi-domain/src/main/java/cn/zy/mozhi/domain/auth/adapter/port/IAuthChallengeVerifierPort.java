package cn.zy.mozhi.domain.auth.adapter.port;

import cn.zy.mozhi.domain.auth.model.valobj.AuthRequestContext;

public interface IAuthChallengeVerifierPort {

    boolean verify(String challengeToken, AuthRequestContext requestContext);
}
