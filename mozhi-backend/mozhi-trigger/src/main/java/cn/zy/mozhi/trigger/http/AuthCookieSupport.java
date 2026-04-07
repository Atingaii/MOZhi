package cn.zy.mozhi.trigger.http;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

public final class AuthCookieSupport {

    public static final String REFRESH_COOKIE_NAME = "mozhi_refresh_token";

    private AuthCookieSupport() {
    }

    public static ResponseCookie issue(String refreshToken, Duration ttl, boolean secure) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .sameSite("Strict")
                .path("/api/auth")
                .secure(secure)
                .maxAge(ttl)
                .build();
    }

    public static ResponseCookie clear(boolean secure) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .sameSite("Strict")
                .path("/api/auth")
                .secure(secure)
                .maxAge(Duration.ZERO)
                .build();
    }
}
