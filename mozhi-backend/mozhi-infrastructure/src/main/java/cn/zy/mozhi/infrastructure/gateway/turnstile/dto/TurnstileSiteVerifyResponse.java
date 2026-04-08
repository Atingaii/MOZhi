package cn.zy.mozhi.infrastructure.gateway.turnstile.dto;

public record TurnstileSiteVerifyResponse(
        boolean success,
        String hostname
) {

    public static TurnstileSiteVerifyResponse failed() {
        return new TurnstileSiteVerifyResponse(false, null);
    }
}
