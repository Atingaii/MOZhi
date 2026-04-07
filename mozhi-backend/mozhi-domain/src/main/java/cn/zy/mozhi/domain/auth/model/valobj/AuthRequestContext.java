package cn.zy.mozhi.domain.auth.model.valobj;

public record AuthRequestContext(
        String ip,
        String userAgent
) {

    public AuthRequestContext {
        ip = normalizeOrDefault(ip, "unknown");
        userAgent = normalizeOrDefault(userAgent, "");
    }

    private static String normalizeOrDefault(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
