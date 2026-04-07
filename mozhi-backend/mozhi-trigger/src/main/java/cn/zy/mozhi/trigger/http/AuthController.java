package cn.zy.mozhi.trigger.http;

import cn.zy.mozhi.api.dto.ApiResponse;
import cn.zy.mozhi.api.dto.AuthLoginRequestDTO;
import cn.zy.mozhi.api.dto.AuthTokenResponseDTO;
import cn.zy.mozhi.domain.auth.model.valobj.AuthTokenPair;
import cn.zy.mozhi.domain.auth.service.AuthDomainService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import cn.zy.mozhi.types.enums.ResponseCode;
import cn.zy.mozhi.types.exception.BaseException;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnBean(AuthDomainService.class)
public class AuthController {

    private static final String TOKEN_TYPE = "Bearer";

    private final AuthDomainService authDomainService;
    private final boolean cookieSecure;

    public AuthController(AuthDomainService authDomainService,
                          @Value("${mozhi.auth.cookie-secure:false}") boolean cookieSecure) {
        this.authDomainService = authDomainService;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponseDTO> login(@Valid @RequestBody AuthLoginRequestDTO requestDTO,
                                                   HttpServletRequest request,
                                                   HttpServletResponse response) {
        AuthTokenPair tokenPair = authDomainService.login(
                requestDTO.identifier(),
                requestDTO.password(),
                requestDTO.challengeToken(),
                AuthRequestContextResolver.resolve(request)
        );
        response.addHeader(HttpHeaders.SET_COOKIE, issueRefreshCookie(tokenPair).toString());
        return ApiResponse.success(toResponse(tokenPair));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponseDTO> refresh(@CookieValue(name = AuthCookieSupport.REFRESH_COOKIE_NAME, required = false) String refreshToken,
                                                     HttpServletRequest request,
                                                     HttpServletResponse response) {
        assertRefreshCookiePresent(refreshToken);
        AuthTokenPair tokenPair = authDomainService.refresh(refreshToken, AuthRequestContextResolver.resolve(request));
        response.addHeader(HttpHeaders.SET_COOKIE, issueRefreshCookie(tokenPair).toString());
        return ApiResponse.success(toResponse(tokenPair));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
                                    @CookieValue(name = AuthCookieSupport.REFRESH_COOKIE_NAME, required = false) String refreshToken,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        assertRefreshCookiePresent(refreshToken);
        authDomainService.logout(
                extractBearerToken(authorizationHeader),
                refreshToken,
                AuthRequestContextResolver.resolve(request)
        );
        response.addHeader(HttpHeaders.SET_COOKIE, AuthCookieSupport.clear(cookieSecure).toString());
        return ApiResponse.success();
    }

    @PostMapping("/logout/all")
    public ApiResponse<Void> logoutAll(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        authDomainService.logoutAll(
                extractBearerToken(authorizationHeader),
                AuthRequestContextResolver.resolve(request)
        );
        response.addHeader(HttpHeaders.SET_COOKIE, AuthCookieSupport.clear(cookieSecure).toString());
        return ApiResponse.success();
    }

    private AuthTokenResponseDTO toResponse(AuthTokenPair tokenPair) {
        return new AuthTokenResponseDTO(
                TOKEN_TYPE,
                tokenPair.accessToken(),
                tokenPair.accessTokenExpiresAt()
        );
    }

    private String extractBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new BaseException(ResponseCode.UNAUTHORIZED, "authorization header is invalid");
        }
        return authorizationHeader.substring(7).trim();
    }

    private void assertRefreshCookiePresent(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BaseException(ResponseCode.UNAUTHORIZED, "refresh token is invalid");
        }
    }

    private ResponseCookie issueRefreshCookie(AuthTokenPair tokenPair) {
        Duration ttl = Duration.between(Instant.now(), tokenPair.refreshTokenExpiresAt());
        return AuthCookieSupport.issue(
                tokenPair.refreshToken(),
                ttl.isNegative() ? Duration.ZERO : ttl,
                cookieSecure
        );
    }
}
