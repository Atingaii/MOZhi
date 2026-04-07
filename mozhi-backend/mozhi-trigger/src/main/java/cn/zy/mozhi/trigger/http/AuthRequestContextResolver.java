package cn.zy.mozhi.trigger.http;

import cn.zy.mozhi.domain.auth.model.valobj.AuthRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

final class AuthRequestContextResolver {

    private AuthRequestContextResolver() {
    }

    static AuthRequestContext resolve(HttpServletRequest request) {
        return new AuthRequestContext(resolveIp(request), request.getHeader(HttpHeaders.USER_AGENT));
    }

    private static String resolveIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int commaIndex = forwardedFor.indexOf(',');
            return commaIndex >= 0 ? forwardedFor.substring(0, commaIndex).trim() : forwardedFor.trim();
        }
        return request.getRemoteAddr();
    }
}
