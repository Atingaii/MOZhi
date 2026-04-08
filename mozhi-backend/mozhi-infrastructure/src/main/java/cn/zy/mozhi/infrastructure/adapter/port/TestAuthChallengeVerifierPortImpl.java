package cn.zy.mozhi.infrastructure.adapter.port;

import cn.zy.mozhi.domain.auth.adapter.port.IAuthChallengeVerifierPort;
import cn.zy.mozhi.domain.auth.model.valobj.AuthRequestContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mozhi.auth.challenge", name = "provider", havingValue = "test")
public class TestAuthChallengeVerifierPortImpl implements IAuthChallengeVerifierPort {

    @Override
    public boolean verify(String challengeToken, AuthRequestContext requestContext) {
        return "test-pass-token".equals(challengeToken);
    }
}
