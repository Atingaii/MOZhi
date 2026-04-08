package cn.zy.mozhi.infrastructure.adapter.port;

import cn.zy.mozhi.domain.auth.adapter.port.IAuthChallengeVerifierPort;
import cn.zy.mozhi.domain.auth.model.valobj.AuthRequestContext;
import cn.zy.mozhi.infrastructure.gateway.turnstile.TurnstileSiteVerifyGateway;
import cn.zy.mozhi.infrastructure.gateway.turnstile.dto.TurnstileSiteVerifyResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "mozhi.auth.challenge",
        name = "provider",
        havingValue = "turnstile",
        matchIfMissing = true
)
public class TurnstileAuthChallengeVerifierPortImpl implements IAuthChallengeVerifierPort {

    private final TurnstileSiteVerifyGateway turnstileSiteVerifyGateway;
    private final List<String> allowedHostnames;
    private final boolean allowBypassWhenUnconfigured;

    public TurnstileAuthChallengeVerifierPortImpl(
            TurnstileSiteVerifyGateway turnstileSiteVerifyGateway,
            @Value("${mozhi.auth.challenge.turnstile.allowed-hostnames:}") String allowedHostnames,
            @Value("${mozhi.auth.challenge.allow-bypass-when-unconfigured:false}") boolean allowBypassWhenUnconfigured
    ) {
        this.turnstileSiteVerifyGateway = turnstileSiteVerifyGateway;
        this.allowedHostnames = Arrays.stream(allowedHostnames.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        this.allowBypassWhenUnconfigured = allowBypassWhenUnconfigured;
    }

    @Override
    public boolean verify(String challengeToken, AuthRequestContext requestContext) {
        if (!turnstileSiteVerifyGateway.isConfigured() || allowedHostnames.isEmpty()) {
            return allowBypassWhenUnconfigured;
        }

        if (challengeToken == null || challengeToken.isBlank()) {
            return false;
        }

        TurnstileSiteVerifyResponse response = turnstileSiteVerifyGateway.verify(challengeToken, requestContext.ip());
        if (!response.success() || response.hostname() == null) {
            return false;
        }

        return allowedHostnames.contains(response.hostname());
    }
}
