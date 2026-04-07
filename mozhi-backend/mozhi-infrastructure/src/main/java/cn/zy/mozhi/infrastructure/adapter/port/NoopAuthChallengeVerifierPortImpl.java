package cn.zy.mozhi.infrastructure.adapter.port;

import cn.zy.mozhi.domain.auth.adapter.port.IAuthChallengeVerifierPort;
import cn.zy.mozhi.domain.auth.model.valobj.AuthRequestContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NoopAuthChallengeVerifierPortImpl implements IAuthChallengeVerifierPort {

    private final String provider;
    private final String noopPassToken;

    public NoopAuthChallengeVerifierPortImpl(
            @Value("${mozhi.auth.challenge.provider:noop}") String provider,
            @Value("${mozhi.auth.challenge.noop-pass-token:dev-pass}") String noopPassToken
    ) {
        this.provider = provider;
        this.noopPassToken = noopPassToken;
    }

    @Override
    public boolean verify(String challengeToken, AuthRequestContext requestContext) {
        if (!"noop".equalsIgnoreCase(provider)) {
            return false;
        }
        return challengeToken != null && challengeToken.equals(noopPassToken);
    }
}
