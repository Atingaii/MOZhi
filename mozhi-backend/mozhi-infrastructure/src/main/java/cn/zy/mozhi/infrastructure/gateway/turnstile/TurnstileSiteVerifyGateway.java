package cn.zy.mozhi.infrastructure.gateway.turnstile;

import cn.zy.mozhi.infrastructure.gateway.turnstile.dto.TurnstileSiteVerifyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class TurnstileSiteVerifyGateway {

    private static final Logger logger = LoggerFactory.getLogger(TurnstileSiteVerifyGateway.class);

    private final String secretKey;
    private final String siteVerifyUrl;
    private final RestClient restClient;

    public TurnstileSiteVerifyGateway(
            RestClient.Builder restClientBuilder,
            @Value("${mozhi.auth.challenge.turnstile.secret-key:}") String secretKey,
            @Value("${mozhi.auth.challenge.turnstile.site-verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}") String siteVerifyUrl,
            @Value("${mozhi.auth.challenge.turnstile.connect-timeout:3s}") Duration connectTimeout,
            @Value("${mozhi.auth.challenge.turnstile.read-timeout:3s}") Duration readTimeout
    ) {
        this.secretKey = secretKey;
        this.siteVerifyUrl = siteVerifyUrl;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    public TurnstileSiteVerifyResponse verify(String challengeToken, String remoteIp) {
        if (secretKey == null || secretKey.isBlank()) {
            logger.warn("Turnstile secret key is missing. Failing challenge verification closed.");
            return TurnstileSiteVerifyResponse.failed();
        }

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("secret", secretKey);
        formData.add("response", challengeToken);
        if (remoteIp != null && !remoteIp.isBlank() && !"unknown".equalsIgnoreCase(remoteIp)) {
            formData.add("remoteip", remoteIp);
        }

        try {
            TurnstileSiteVerifyResponse response = restClient.post()
                    .uri(siteVerifyUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(TurnstileSiteVerifyResponse.class);
            return response == null ? TurnstileSiteVerifyResponse.failed() : response;
        } catch (RuntimeException exception) {
            logger.warn("Turnstile verification request failed.", exception);
            return TurnstileSiteVerifyResponse.failed();
        }
    }
}
