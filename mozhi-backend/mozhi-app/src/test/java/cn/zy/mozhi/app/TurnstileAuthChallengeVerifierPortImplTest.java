package cn.zy.mozhi.app;

import cn.zy.mozhi.domain.auth.model.valobj.AuthRequestContext;
import cn.zy.mozhi.infrastructure.adapter.port.TurnstileAuthChallengeVerifierPortImpl;
import cn.zy.mozhi.infrastructure.gateway.turnstile.TurnstileSiteVerifyGateway;
import cn.zy.mozhi.infrastructure.gateway.turnstile.dto.TurnstileSiteVerifyResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TurnstileAuthChallengeVerifierPortImplTest {

    @Test
    void should_reject_response_when_turnstile_reports_failure() {
        TurnstileSiteVerifyGateway gateway = mock(TurnstileSiteVerifyGateway.class);
        when(gateway.isConfigured()).thenReturn(true);
        when(gateway.verify("token", "127.0.0.1"))
                .thenReturn(new TurnstileSiteVerifyResponse(false, "localhost"));

        TurnstileAuthChallengeVerifierPortImpl verifier = new TurnstileAuthChallengeVerifierPortImpl(gateway, "localhost", false);

        boolean verified = verifier.verify("token", new AuthRequestContext("127.0.0.1", "JUnit"));

        assertThat(verified).isFalse();
    }

    @Test
    void should_reject_response_when_hostname_is_not_allowlisted() {
        TurnstileSiteVerifyGateway gateway = mock(TurnstileSiteVerifyGateway.class);
        when(gateway.isConfigured()).thenReturn(true);
        when(gateway.verify("token", "127.0.0.1"))
                .thenReturn(new TurnstileSiteVerifyResponse(true, "evil.example"));

        TurnstileAuthChallengeVerifierPortImpl verifier = new TurnstileAuthChallengeVerifierPortImpl(gateway, "localhost", false);

        boolean verified = verifier.verify("token", new AuthRequestContext("127.0.0.1", "JUnit"));

        assertThat(verified).isFalse();
    }

    @Test
    void should_accept_response_when_turnstile_succeeds_for_allowlisted_hostname() {
        TurnstileSiteVerifyGateway gateway = mock(TurnstileSiteVerifyGateway.class);
        when(gateway.isConfigured()).thenReturn(true);
        when(gateway.verify("token", "127.0.0.1"))
                .thenReturn(new TurnstileSiteVerifyResponse(true, "localhost"));

        TurnstileAuthChallengeVerifierPortImpl verifier = new TurnstileAuthChallengeVerifierPortImpl(gateway, "localhost,127.0.0.1", false);

        boolean verified = verifier.verify("token", new AuthRequestContext("127.0.0.1", "JUnit"));

        assertThat(verified).isTrue();
    }

    @Test
    void should_allow_bypass_when_turnstile_is_unconfigured_and_dev_bypass_is_enabled() {
        TurnstileSiteVerifyGateway gateway = mock(TurnstileSiteVerifyGateway.class);
        when(gateway.isConfigured()).thenReturn(false);

        TurnstileAuthChallengeVerifierPortImpl verifier = new TurnstileAuthChallengeVerifierPortImpl(gateway, "localhost,127.0.0.1", true);

        boolean verified = verifier.verify("", new AuthRequestContext("127.0.0.1", "JUnit"));

        assertThat(verified).isTrue();
    }

    @Test
    void should_reject_when_turnstile_is_unconfigured_and_dev_bypass_is_disabled() {
        TurnstileSiteVerifyGateway gateway = mock(TurnstileSiteVerifyGateway.class);
        when(gateway.isConfigured()).thenReturn(false);

        TurnstileAuthChallengeVerifierPortImpl verifier = new TurnstileAuthChallengeVerifierPortImpl(gateway, "localhost,127.0.0.1", false);

        boolean verified = verifier.verify("", new AuthRequestContext("127.0.0.1", "JUnit"));

        assertThat(verified).isFalse();
    }
}
