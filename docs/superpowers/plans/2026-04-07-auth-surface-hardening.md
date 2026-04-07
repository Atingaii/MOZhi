# Auth Surface Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the login/register surface to match the approved desktop reference while replacing localStorage refresh sessions with `HttpOnly` cookie refresh and adding password, abuse, and audit hardening suitable for production launch.

**Architecture:** Implement this in four slices: backend auth contract migration, backend security hardening, frontend auth/session refactor, and frontend auth shell recreation. Keep all auth rules inside the existing DDD boundaries (`domain` owns policy, `infrastructure` owns Redis/challenge/password implementations, `trigger` owns HTTP/cookie wiring). On the web side, keep API work in the centralized client layer, keep page components compositional, and isolate `/auth` from the main shell so the screen can fully match the reference layout.

**Tech Stack:** Spring Boot 3, Spring Security, Redis, RS256 JWT, Argon2 + BCrypt delegating encoder, React 18, Vite, Axios, Zustand, TanStack Query, Vitest, React Testing Library, jsdom.

---

### Task 1: Lock the cookie-based auth contract in backend tests

**Files:**
- Modify: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/AuthHttpIntegrationTest.java`
- Modify: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/AuthLoginRequestDTO.java`
- Modify: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/AuthRefreshRequestDTO.java`
- Modify: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/AuthLogoutRequestDTO.java`
- Modify: `mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/AuthTokenResponseDTO.java`
- Create: `mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/AuthCookieSupport.java`
- Modify: `mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/AuthController.java`

- [ ] **Step 1: Write the failing cookie-contract tests**

```java
@Test
void should_set_refresh_cookie_on_login_and_hide_refresh_token_from_body() throws Exception {
    registerUser("alice", "alice@mozhi.dev", "Secret123!", "Alice");

    mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"identifier":"alice","password":"Secret123!"}
                        """))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("mozhi_refresh_token=")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
            .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
}

@Test
void should_refresh_from_cookie_without_refresh_token_body() throws Exception {
    registerUser("alice", "alice@mozhi.dev", "Secret123!", "Alice");
    MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"identifier":"alice","password":"Secret123!"}
                        """))
            .andExpect(status().isOk())
            .andReturn();

    String setCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    Cookie refreshCookie = new Cookie("mozhi_refresh_token", extractCookieValue(setCookie, "mozhi_refresh_token"));

    mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("mozhi_refresh_token=")))
            .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
}

@Test
void should_clear_refresh_cookie_on_logout() throws Exception {
    registerUser("alice", "alice@mozhi.dev", "Secret123!", "Alice");
    MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"identifier":"alice","password":"Secret123!"}
                        """))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode data = readData(loginResult);
    Cookie refreshCookie = new Cookie("mozhi_refresh_token", extractCookieValue(
            loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE), "mozhi_refresh_token"));

    mockMvc.perform(post("/api/auth/logout")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + data.path("accessToken").asText())
                    .cookie(refreshCookie))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
}
```

- [ ] **Step 2: Run the targeted auth test class and confirm it fails on the old body-based contract**

Run:

```powershell
Set-Location 'F:\new_opint\VibeCoding\MOZhi\mozhi-backend'
.\mvnw.cmd -q -pl mozhi-app -am -Dtest=AuthHttpIntegrationTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL with request DTO mismatch (`username` instead of `identifier`), refresh expecting a body token, and response assertions failing because `refreshToken` is still returned in JSON instead of cookie-only.

- [ ] **Step 3: Implement the minimal cookie contract**

Use these focused changes:

```java
public record AuthLoginRequestDTO(
        @NotBlank(message = "identifier must not be blank") String identifier,
        @NotBlank(message = "password must not be blank") String password,
        String challengeToken
) {
}

public record AuthTokenResponseDTO(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt
) {
}
```

```java
public final class AuthCookieSupport {

    public static final String REFRESH_COOKIE_NAME = "mozhi_refresh_token";

    private AuthCookieSupport() {}

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
```

```java
@PostMapping("/login")
public ApiResponse<AuthTokenResponseDTO> login(@Valid @RequestBody AuthLoginRequestDTO requestDTO,
                                               HttpServletResponse response) {
    AuthTokenPair tokenPair = authDomainService.login(requestDTO.identifier(), requestDTO.password(), requestDTO.challengeToken());
    response.addHeader(HttpHeaders.SET_COOKIE, AuthCookieSupport.issue(
            tokenPair.refreshToken(),
            Duration.between(Instant.now(), tokenPair.refreshTokenExpiresAt()),
            authSecurityProperties.cookieSecure()
    ).toString());
    return ApiResponse.success(new AuthTokenResponseDTO("Bearer", tokenPair.accessToken(), tokenPair.accessTokenExpiresAt()));
}

@PostMapping("/refresh")
public ApiResponse<AuthTokenResponseDTO> refresh(@CookieValue(name = AuthCookieSupport.REFRESH_COOKIE_NAME) String refreshToken,
                                                 HttpServletResponse response) {
    AuthTokenPair tokenPair = authDomainService.refresh(refreshToken);
    response.addHeader(HttpHeaders.SET_COOKIE, AuthCookieSupport.issue(
            tokenPair.refreshToken(),
            Duration.between(Instant.now(), tokenPair.refreshTokenExpiresAt()),
            authSecurityProperties.cookieSecure()
    ).toString());
    return ApiResponse.success(new AuthTokenResponseDTO("Bearer", tokenPair.accessToken(), tokenPair.accessTokenExpiresAt()));
}
```

- [ ] **Step 4: Re-run the targeted auth test class until it passes**

Run:

```powershell
Set-Location 'F:\new_opint\VibeCoding\MOZhi\mozhi-backend'
.\mvnw.cmd -q -pl mozhi-app -am -Dtest=AuthHttpIntegrationTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS for the new cookie issuance, cookie refresh, and cookie clearing assertions.

- [ ] **Step 5: Commit the contract migration slice**

```bash
git add mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/AuthLoginRequestDTO.java \
        mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/AuthRefreshRequestDTO.java \
        mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/AuthLogoutRequestDTO.java \
        mozhi-backend/mozhi-api/src/main/java/cn/zy/mozhi/api/dto/AuthTokenResponseDTO.java \
        mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/AuthHttpIntegrationTest.java \
        mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/AuthCookieSupport.java \
        mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/AuthController.java
git commit -m "feat(auth): move refresh contract to http-only cookie"
```

### Task 2: Enforce the new password policy and hash migration path

**Files:**
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/user/adapter/port/IUserPasswordBlocklistPort.java`
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/user/service/UserPasswordPolicy.java`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/CommonPasswordBlocklistPortImpl.java`
- Create: `mozhi-backend/mozhi-app/src/main/resources/security/common-passwords.txt`
- Modify: `mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/DomainConfiguration.java`
- Modify: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/user/service/UserDomainService.java`
- Modify: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/UserPasswordEncoderPortImpl.java`
- Modify: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/UserHttpIntegrationTest.java`
- Modify: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/AuthHttpIntegrationTest.java`

- [ ] **Step 1: Add failing tests for short, common, and legacy-password scenarios**

```java
@Test
void should_reject_registration_when_password_is_shorter_than_eight_chars() throws Exception {
    mockMvc.perform(post("/api/user/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"username":"shorty","email":"shorty@mozhi.dev","password":"1234567","nickname":"Shorty"}
                        """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("password must be at least 8 characters"));
}

@Test
void should_reject_registration_when_password_is_common() throws Exception {
    mockMvc.perform(post("/api/user/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"username":"commoner","email":"commoner@mozhi.dev","password":"password123","nickname":"Common"}
                        """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("password is too weak"));
}

@Test
void should_allow_login_for_existing_bcrypt_hashes_after_argon2_becomes_default() throws Exception {
    String legacyHash = new BCryptPasswordEncoder().encode("LegacyPass8");
    jdbcTemplate.update("""
        INSERT INTO `user` (username, email, password_hash, nickname, avatar_url, bio, status, created_at, updated_at)
        VALUES (?, ?, ?, ?, NULL, NULL, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """, "legacy", "legacy@mozhi.dev", legacyHash, "Legacy");

    mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"identifier":"legacy","password":"LegacyPass8"}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
}
```

- [ ] **Step 2: Run the user/auth integration classes and confirm they fail**

Run:

```powershell
Set-Location 'F:\new_opint\VibeCoding\MOZhi\mozhi-backend'
.\mvnw.cmd -q -pl mozhi-app -am -Dtest=UserHttpIntegrationTest,AuthHttpIntegrationTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL because the current registration flow only checks blank passwords, does not block common passwords, and still uses raw BCrypt as the only hash strategy.

- [ ] **Step 3: Implement the policy object, blocklist port, and delegating encoder**

```java
public class UserPasswordPolicy {

    private final IUserPasswordBlocklistPort passwordBlocklistPort;

    public UserPasswordPolicy(IUserPasswordBlocklistPort passwordBlocklistPort) {
        this.passwordBlocklistPort = passwordBlocklistPort;
    }

    public void validate(String rawPassword, String username, String email) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "password must not be blank");
        }
        if (rawPassword.length() < 8) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "password must be at least 8 characters");
        }
        if (rawPassword.length() > 64) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "password must be at most 64 characters");
        }
        if (passwordBlocklistPort.contains(rawPassword)) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "password is too weak");
        }
        String emailLocalPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        if (rawPassword.equalsIgnoreCase(username) || rawPassword.equalsIgnoreCase(emailLocalPart)) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "password is too weak");
        }
    }
}
```

```java
public class UserPasswordEncoderPortImpl implements IUserPasswordEncoderPort {

    private final PasswordEncoder passwordEncoder;

    public UserPasswordEncoderPortImpl() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        this.passwordEncoder = new DelegatingPasswordEncoder("argon2", encoders);
    }

    @Override
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
```

```java
public UserEntity register(String username, String email, String rawPassword, String nickname) {
    String normalizedUsername = requireText(username, "username must not be blank");
    String normalizedEmail = requireEmail(email);
    passwordPolicy.validate(rawPassword, normalizedUsername, normalizedEmail);
    String normalizedNickname = normalizeNickname(nickname, normalizedUsername);
    if (userRepository.findByUsername(normalizedUsername).isPresent()) {
        throw new BaseException(ResponseCode.BAD_REQUEST, "username already exists");
    }
    if (userRepository.findByEmail(normalizedEmail).isPresent()) {
        throw new BaseException(ResponseCode.BAD_REQUEST, "email already exists");
    }

    UserEntity userEntity = UserEntity.createNew(
            normalizedUsername,
            normalizedEmail,
            userPasswordEncoderPort.encode(rawPassword),
            normalizedNickname
    );
    userEntity.setId(userRepository.save(userEntity));
    return userEntity;
}
```

- [ ] **Step 4: Re-run the focused tests and verify the password policy is green**

Run:

```powershell
Set-Location 'F:\new_opint\VibeCoding\MOZhi\mozhi-backend'
.\mvnw.cmd -q -pl mozhi-app -am -Dtest=UserHttpIntegrationTest,AuthHttpIntegrationTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS for short-password rejection, common-password rejection, and legacy bcrypt login compatibility. New registered users should now receive `{argon2}`-prefixed hashes.

- [ ] **Step 5: Commit the password-hardening slice**

```bash
git add mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/user/adapter/port/IUserPasswordBlocklistPort.java \
        mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/user/service/UserPasswordPolicy.java \
        mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/CommonPasswordBlocklistPortImpl.java \
        mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/UserPasswordEncoderPortImpl.java \
        mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/DomainConfiguration.java \
        mozhi-backend/mozhi-app/src/main/resources/security/common-passwords.txt \
        mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/user/service/UserDomainService.java \
        mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/UserHttpIntegrationTest.java \
        mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/AuthHttpIntegrationTest.java
git commit -m "feat(auth): add password policy and argon2 default hashing"
```

### Task 3: Add abuse controls, challenge escalation, and auth audit events

**Files:**
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/adapter/port/IAuthAttemptGuardPort.java`
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/adapter/port/IAuthChallengeVerifierPort.java`
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/adapter/port/IAuthAuditPort.java`
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/model/valobj/AuthRequestContext.java`
- Create: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthSecurityPolicyService.java`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthAttemptGuardPortImpl.java`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/NoopAuthChallengeVerifierPortImpl.java`
- Create: `mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/StructuredLogAuthAuditPortImpl.java`
- Create: `mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/AuthSecurityProperties.java`
- Modify: `mozhi-backend/mozhi-app/src/main/resources/application.yml`
- Modify: `mozhi-backend/mozhi-types/src/main/java/cn/zy/mozhi/types/enums/ResponseCode.java`
- Modify: `mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/GlobalExceptionHandler.java`
- Modify: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthDomainService.java`
- Modify: `mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/user/service/UserDomainService.java`
- Modify: `mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/AuthController.java`
- Modify: `mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/UserController.java`
- Modify: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/AuthHttpIntegrationTest.java`
- Modify: `mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/UserHttpIntegrationTest.java`

- [ ] **Step 1: Add failing abuse-path tests before touching policy code**

```java
@Test
void should_require_challenge_after_five_failed_login_attempts() throws Exception {
    registerUser("alice", "alice@mozhi.dev", "Secret123!", "Alice");

    for (int index = 0; index < 5; index++) {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"identifier":"alice","password":"WrongPass8"}
                            """))
                .andExpect(status().isUnauthorized());
    }

    mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"identifier":"alice","password":"Secret123!"}
                        """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("A0410"));
}

@Test
void should_temporarily_lock_login_after_ten_failed_attempts() throws Exception {
    registerUser("alice", "alice@mozhi.dev", "Secret123!", "Alice");

    for (int index = 0; index < 10; index++) {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"identifier":"alice","password":"WrongPass8","challengeToken":"dev-pass"}
                            """))
                .andExpect(status().isUnauthorized());
    }

    mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"identifier":"alice","password":"Secret123!","challengeToken":"dev-pass"}
                        """))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("A0429"));
}

@Test
void should_require_register_challenge_after_three_ip_attempts() throws Exception {
    for (int index = 0; index < 3; index++) {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"username":"dup","email":"dup@mozhi.dev","password":"Secret123!","nickname":"Dup"}
                            """));
    }

    mockMvc.perform(post("/api/user/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"username":"fresh","email":"fresh@mozhi.dev","password":"Secret123!","nickname":"Fresh"}
                        """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("A0410"));
}
```

- [ ] **Step 2: Run the auth/user integration tests and confirm the new abuse cases fail**

Run:

```powershell
Set-Location 'F:\new_opint\VibeCoding\MOZhi\mozhi-backend'
.\mvnw.cmd -q -pl mozhi-app -am -Dtest=AuthHttpIntegrationTest,UserHttpIntegrationTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL because there is no attempt guard, no challenge code, no 429 mapping, and no request-context-aware audit/policy layer yet.

- [ ] **Step 3: Implement Redis-backed attempt guard, noop challenge verifier, audit sink, and response codes**

```java
public enum ResponseCode {

    SUCCESS("0000", "success"),
    BAD_REQUEST("A0400", "bad request"),
    UNAUTHORIZED("A0401", "unauthorized"),
    AUTH_CHALLENGE_REQUIRED("A0410", "auth challenge required"),
    TOO_MANY_REQUESTS("A0429", "too many requests"),
    FORBIDDEN("A0403", "forbidden"),
    NOT_FOUND("A0404", "not found"),
    SYSTEM_ERROR("B0001", "system error");
}
```

```java
public class AuthSecurityPolicyService {

    public void assertLoginAllowed(String identifier, String challengeToken, AuthRequestContext context) {
        if (attemptGuardPort.isIpRateLimited(context.ip())) {
            auditPort.record("login_rate_limited", context, identifier);
            throw new BaseException(ResponseCode.TOO_MANY_REQUESTS, "try again later");
        }
        if (attemptGuardPort.requiresChallenge(identifier, context.ip())
                && !challengeVerifierPort.verify(challengeToken, context.ip())) {
            auditPort.record("login_challenge_required", context, identifier);
            throw new BaseException(ResponseCode.AUTH_CHALLENGE_REQUIRED, "challenge required");
        }
        if (attemptGuardPort.isLocked(identifier)) {
            auditPort.record("login_locked", context, identifier);
            throw new BaseException(ResponseCode.TOO_MANY_REQUESTS, "try again later");
        }
    }
}
```

```java
public record AuthRequestContext(
        String ip,
        String userAgent
) {
}
```

```yaml
mozhi:
  auth:
    cookie-secure: ${MOZHI_AUTH_COOKIE_SECURE:false}
    challenge:
      provider: ${MOZHI_AUTH_CHALLENGE_PROVIDER:noop}
      noop-pass-token: ${MOZHI_AUTH_CHALLENGE_NOOP_PASS_TOKEN:dev-pass}
    limits:
      login-ip-max-attempts: 20
      login-ip-window-minutes: 10
      login-identifier-challenge-threshold: 5
      login-identifier-lock-threshold: 10
      login-identifier-window-minutes: 15
      register-ip-max-attempts: 5
      register-ip-challenge-threshold: 3
```

- [ ] **Step 4: Re-run the abuse-path tests and make them pass**

Run:

```powershell
Set-Location 'F:\new_opint\VibeCoding\MOZhi\mozhi-backend'
.\mvnw.cmd -q -pl mozhi-app -am -Dtest=AuthHttpIntegrationTest,UserHttpIntegrationTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS for challenge escalation, temporary login lock, register challenge trigger, and new response-code/status mapping.

- [ ] **Step 5: Commit the auth-hardening policy slice**

```bash
git add mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/adapter/port/IAuthAttemptGuardPort.java \
        mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/adapter/port/IAuthChallengeVerifierPort.java \
        mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/adapter/port/IAuthAuditPort.java \
        mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/model/valobj/AuthRequestContext.java \
        mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthSecurityPolicyService.java \
        mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/RedisAuthAttemptGuardPortImpl.java \
        mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/NoopAuthChallengeVerifierPortImpl.java \
        mozhi-backend/mozhi-infrastructure/src/main/java/cn/zy/mozhi/infrastructure/adapter/port/StructuredLogAuthAuditPortImpl.java \
        mozhi-backend/mozhi-app/src/main/java/cn/zy/mozhi/app/config/AuthSecurityProperties.java \
        mozhi-backend/mozhi-app/src/main/resources/application.yml \
        mozhi-backend/mozhi-types/src/main/java/cn/zy/mozhi/types/enums/ResponseCode.java \
        mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/GlobalExceptionHandler.java \
        mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/auth/service/AuthDomainService.java \
        mozhi-backend/mozhi-domain/src/main/java/cn/zy/mozhi/domain/user/service/UserDomainService.java \
        mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/AuthController.java \
        mozhi-backend/mozhi-trigger/src/main/java/cn/zy/mozhi/trigger/http/UserController.java \
        mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/AuthHttpIntegrationTest.java \
        mozhi-backend/mozhi-app/src/test/java/cn/zy/mozhi/app/UserHttpIntegrationTest.java
git commit -m "feat(auth): add abuse guard and challenge escalation"
```

### Task 4: Add frontend test infrastructure and isolate `/auth` from the main shell

**Files:**
- Modify: `mozhi-web/package.json`
- Modify: `mozhi-web/vite.config.ts`
- Modify: `mozhi-web/tsconfig.app.json`
- Create: `mozhi-web/src/test/setup.ts`
- Create: `mozhi-web/src/test/renderWithRouter.tsx`
- Create: `mozhi-web/src/layouts/AuthLayout.tsx`
- Modify: `mozhi-web/src/router/index.tsx`
- Create: `mozhi-web/src/router/auth-route.test.tsx`

- [ ] **Step 1: Write the failing auth-route isolation test**

```tsx
import { MemoryRouter } from "react-router-dom";
import { render, screen } from "@testing-library/react";

import AuthLayout from "@/layouts/AuthLayout";

it("renders a minimal auth navbar without the main shell footer", () => {
  render(
    <MemoryRouter initialEntries={["/auth?mode=register"]}>
      <AuthLayout />
    </MemoryRouter>
  );

  expect(screen.getByRole("link", { name: /MOZhi/i })).toBeInTheDocument();
  expect(screen.queryByText(/Built for content, knowledge, community, and commerce/i)).not.toBeInTheDocument();
  expect(screen.queryByPlaceholderText(/搜索专题、问答、创作者或商品/i)).not.toBeInTheDocument();
});
```

- [ ] **Step 2: Run the frontend test command and confirm it fails before the harness exists**

Run:

```powershell
Set-Location 'F:\new_opint\VibeCoding\MOZhi\mozhi-web'
npm run test -- --run src/router/auth-route.test.tsx
```

Expected: FAIL because there is no `test` script yet, no Vitest setup, and no `AuthLayout`.

- [ ] **Step 3: Add Vitest + RTL and split the auth route out of `AppShell`**

```json
{
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "lint": "eslint .",
    "preview": "vite preview",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "devDependencies": {
    "@testing-library/jest-dom": "^6.6.3",
    "@testing-library/react": "^16.1.0",
    "@testing-library/user-event": "^14.5.2",
    "jsdom": "^25.0.1",
    "vitest": "^2.1.8"
  }
}
```

```ts
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url))
    }
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/test/setup.ts"
  },
  server: {
    host: "127.0.0.1",
    port: 5173
  }
});
```

```tsx
export default function AuthLayout() {
  return (
    <div className="mozhi-auth-route-shell">
      <Outlet />
    </div>
  );
}
```

```tsx
export const router = createBrowserRouter([
  {
    path: "/auth",
    element: <AuthLayout />,
    children: [{ index: true, element: <AuthPage /> }]
  },
  {
    path: "/",
    element: <AppShell />,
    children: [
      { index: true, element: <HomePage /> },
      { path: "qa", element: <QAPage /> },
      { path: "search", element: <SearchPage /> },
      { path: "commerce", element: <CommercePage /> },
      {
        element: <ProtectedRoute />,
        children: [
          { path: "following", element: <FollowingPage /> },
          { path: "editor", element: <EditorPage /> },
          { path: "notifications", element: <NotificationsPage /> },
          { path: "profile", element: <ProfilePage /> },
          { path: "settings", element: <SettingsPage /> }
        ]
      }
    ]
  }
]);
```

- [ ] **Step 4: Re-run the frontend auth-route test and make it pass**

Run:

```powershell
Set-Location 'F:\new_opint\VibeCoding\MOZhi\mozhi-web'
npm install
npm run test -- --run src/router/auth-route.test.tsx
```

Expected: PASS, with `/auth` no longer rendering the main-shell footer or command bar.

- [ ] **Step 5: Commit the frontend test harness and route split**

```bash
git add mozhi-web/package.json \
        mozhi-web/vite.config.ts \
        mozhi-web/tsconfig.app.json \
        mozhi-web/src/test/setup.ts \
        mozhi-web/src/test/renderWithRouter.tsx \
        mozhi-web/src/layouts/AuthLayout.tsx \
        mozhi-web/src/router/index.tsx \
        mozhi-web/src/router/auth-route.test.tsx
git commit -m "test(web): add auth route harness and vitest setup"
```

### Task 5: Refactor the frontend session model to access-token-in-memory plus cookie refresh

**Files:**
- Modify: `mozhi-web/src/api/client.ts`
- Modify: `mozhi-web/src/api/modules/auth.ts`
- Modify: `mozhi-web/src/stores/useAuthStore.ts`
- Create: `mozhi-web/src/auth/SessionBootstrap.tsx`
- Modify: `mozhi-web/src/App.tsx`
- Modify: `mozhi-web/src/router/guards.tsx`
- Modify: `mozhi-web/src/pages/Profile/index.tsx`
- Create: `mozhi-web/src/api/client.test.ts`
- Create: `mozhi-web/src/router/guards.test.tsx`

- [ ] **Step 1: Write the failing client/bootstrap tests**

```tsx
it("retries a protected request once after cookie refresh succeeds", async () => {
  useAuthStore.getState().markAuthenticated(expiredAccessToken);

  mock.onGet("/user/1").replyOnce(401);
  mock.onPost("/auth/refresh").replyOnce(200, {
    success: true,
    code: "0000",
    message: "success",
    data: {
      tokenType: "Bearer",
      accessToken: freshAccessToken,
      accessTokenExpiresAt: "2026-04-07T12:00:00Z"
    }
  });
  mock.onGet("/user/1").replyOnce(200, {
    success: true,
    code: "0000",
    message: "success",
    data: { userId: 1, username: "alice" }
  });

  const data = await getApi("/user/1");
  expect(data).toEqual({ userId: 1, username: "alice" });
  expect(useAuthStore.getState().accessToken).toBe(freshAccessToken);
});

it("waits for session bootstrap before redirecting a protected route", () => {
  useAuthStore.setState({ status: "anonymous", accessToken: null, bootstrapStatus: "loading" });
  renderWithRouter("/profile", [
    {
      path: "/profile",
      element: <ProtectedRoute />,
      children: [{ path: "/profile", element: <div>profile</div> }]
    }
  ]);

  expect(screen.queryByText("profile")).not.toBeInTheDocument();
  expect(screen.queryByRole("link", { name: /登录/i })).not.toBeInTheDocument();
});
```

- [ ] **Step 2: Run the new frontend tests and confirm they fail**

Run:

```powershell
Set-Location 'F:\new_opint\VibeCoding\MOZhi\mozhi-web'
npm run test -- --run src/api/client.test.ts src/router/guards.test.tsx
```

Expected: FAIL because the current client still depends on a persisted `refreshToken`, the store contract is wrong for cookie refresh, and the guards do not have a bootstrap state.

- [ ] **Step 3: Implement the in-memory store, credentialed refresh, and bootstrap gate**

```ts
interface AuthState {
  readonly status: "anonymous" | "authenticated";
  readonly accessToken: string | null;
  readonly user: AuthIdentity | null;
  readonly bootstrapStatus: "idle" | "loading" | "ready";
  markAuthenticated: (accessToken: string) => void;
  beginBootstrap: () => void;
  finishBootstrap: () => void;
  reset: () => void;
}
```

```ts
export interface AuthAccessTokenResponse {
  readonly tokenType: string;
  readonly accessToken: string;
  readonly accessTokenExpiresAt: string;
}

export async function loginWithPassword(payload: LoginPayload) {
  return postApi<AuthAccessTokenResponse, LoginPayload>(authEndpoints.login, payload, {
    skipAuthRefresh: true,
    withCredentials: true
  });
}

export async function refreshSession() {
  return postApi<AuthAccessTokenResponse>(authEndpoints.refresh, undefined, {
    skipAuthRefresh: true,
    withCredentials: true
  });
}
```

```ts
export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 5000,
  withCredentials: true
});

async function refreshAccessToken(): Promise<AuthAccessTokenResponse> {
  if (!refreshPromise) {
    refreshPromise = authClient
      .post<ApiResponse<AuthAccessTokenResponse>>(`${apiPaths.auth}/refresh`, undefined, {
        timeout: 5000,
        withCredentials: true
      })
      .then((response) => unwrapApiResponse(response.data))
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}
```

```tsx
export function SessionBootstrap() {
  const { bootstrapStatus, beginBootstrap, finishBootstrap, markAuthenticated, reset } = useAuthStore();

  useEffect(() => {
    if (bootstrapStatus !== "idle") {
      return;
    }
    beginBootstrap();
    refreshSession()
      .then((session) => markAuthenticated(session.accessToken))
      .catch(() => reset())
      .finally(() => finishBootstrap());
  }, [bootstrapStatus, beginBootstrap, finishBootstrap, markAuthenticated, reset]);

  return null;
}
```

- [ ] **Step 4: Re-run the focused frontend tests until they pass**

Run:

```powershell
Set-Location 'F:\new_opint\VibeCoding\MOZhi\mozhi-web'
npm run test -- --run src/api/client.test.ts src/router/guards.test.tsx
```

Expected: PASS, proving the client retries through refresh cookie flow and the protected route waits for bootstrap instead of redirecting too early.

- [ ] **Step 5: Commit the frontend session-model refactor**

```bash
git add mozhi-web/src/api/client.ts \
        mozhi-web/src/api/modules/auth.ts \
        mozhi-web/src/stores/useAuthStore.ts \
        mozhi-web/src/auth/SessionBootstrap.tsx \
        mozhi-web/src/App.tsx \
        mozhi-web/src/router/guards.tsx \
        mozhi-web/src/pages/Profile/index.tsx \
        mozhi-web/src/api/client.test.ts \
        mozhi-web/src/router/guards.test.tsx
git commit -m "feat(web): move auth session to cookie refresh bootstrap"
```

### Task 6: Recreate the auth screen to match the approved desktop reference

**Files:**
- Modify: `mozhi-web/src/pages/Auth/index.tsx`
- Modify: `mozhi-web/src/styles/globals.css`
- Modify: `mozhi-web/src/layouts/AuthLayout.tsx`
- Create: `mozhi-web/src/pages/Auth/AuthPage.test.tsx`

- [ ] **Step 1: Write the failing auth-page UI tests**

```tsx
it("renders the desktop register shell with stepper, social row, terms, and brand panel", () => {
  renderWithRouter("/auth?mode=register", [{ path: "/auth", element: <AuthPage /> }]);

  expect(screen.getByText("开启你的创作之旅")).toBeInTheDocument();
  expect(screen.getByText("起步")).toBeInTheDocument();
  expect(screen.getByText("验证")).toBeInTheDocument();
  expect(screen.getByText("开启")).toBeInTheDocument();
  expect(screen.getByRole("checkbox", { name: /服务条款/i })).toBeInTheDocument();
  expect(screen.getByText(/已有 2,400\+/i)).toBeInTheDocument();
});

it("renders the login shell with identifier input and no register-only fields", () => {
  renderWithRouter("/auth?mode=login", [{ path: "/auth", element: <AuthPage /> }]);

  expect(screen.getByLabelText(/用户名或邮箱/i)).toBeInTheDocument();
  expect(screen.queryByLabelText(/邮箱地址/i)).not.toBeInTheDocument();
  expect(screen.queryByRole("checkbox", { name: /服务条款/i })).not.toBeInTheDocument();
});
```

- [ ] **Step 2: Run the auth-page UI test file and confirm it fails on the current shell**

Run:

```powershell
Set-Location 'F:\new_opint\VibeCoding\MOZhi\mozhi-web'
npm run test -- --run src/pages/Auth/AuthPage.test.tsx
```

Expected: FAIL because the current auth page copy, field structure, route shell, and right-side brand composition do not yet match the demo.

- [ ] **Step 3: Implement the new auth page and CSS with desktop-first fidelity**

Use the reference composition directly:

```tsx
export default function AuthPage() {
  const isRegister = mode === "register";
  const infoMessage = socialInfo;

  return (
    <div className="mozhi-auth-reference">
      <nav className="mozhi-auth-navbar">
        <Link to="/" className="mozhi-auth-brand">
          <img alt="MOZhi" src={brandMark} />
          <span>MOZhi</span>
        </Link>
        <div className="mozhi-auth-nav-actions">
          <button type="button" onClick={() => switchMode("login")}>登录</button>
          <button type="button" onClick={() => switchMode("register")}>注册</button>
        </div>
      </nav>

      <div className="mozhi-auth-page-container">
        <section className="mozhi-auth-form-side">
          <div className="mozhi-auth-form-wrapper">
            <div className="mozhi-auth-steps">
              <span className="mozhi-auth-step is-active">1 起步</span>
              <span className="mozhi-auth-step-line" />
              <span className="mozhi-auth-step">2 验证</span>
              <span className="mozhi-auth-step-line" />
              <span className="mozhi-auth-step">3 开启</span>
            </div>
            <div className="mozhi-auth-form-header">
              <h1>{isRegister ? "开启你的创作之旅" : "欢迎回到 MOZhi"}</h1>
              <p>{isRegister ? "已有账号？立即登录" : "没有账号？立即注册"}</p>
            </div>
            <div className="mozhi-auth-social-row">
              <button className="mozhi-auth-social-btn" onClick={() => setSocialInfo("即将支持 Google 登录")} type="button"><GoogleIcon /></button>
              <button className="mozhi-auth-social-btn" onClick={() => setSocialInfo("即将支持 Facebook 登录")} type="button"><FacebookIcon /></button>
              <button className="mozhi-auth-social-btn" onClick={() => setSocialInfo("即将支持 GitHub 登录")} type="button"><GithubIcon /></button>
            </div>
            <div className="mozhi-auth-divider">或通过邮箱</div>
            <form onSubmit={handleSubmit}>
              {isRegister ? <RegisterFields /> : <LoginFields />}
              {infoMessage ? <p className="mozhi-auth-info">{infoMessage}</p> : null}
              {isRegister ? <TermsRow /> : null}
              <button className="mozhi-auth-submit-btn" type="submit">
                {isRegister ? "注册 MOZhi 账号" : "登录 MOZhi"}
              </button>
            </form>
          </div>
        </section>
        <aside className="mozhi-auth-brand-side">
          <div className="mozhi-auth-illustration-card" />
          <p className="mozhi-auth-brand-quote">"这里是创作者和读者的精神家园。"</p>
          <div className="mozhi-auth-feature-list">
            <div>创作台：沉浸式写作与一键发布</div>
            <div>版权保护：为你的内容保驾护航</div>
            <div>知识商城：让你的才华产生价值</div>
          </div>
          <p className="mozhi-auth-social-proof">已有 2,400+ 成员加入</p>
        </aside>
      </div>
    </div>
  );
}
```

```css
.mozhi-auth-reference {
  min-height: 100vh;
  background: #fff;
  color: #1a1a1a;
}

.mozhi-auth-navbar {
  height: 56px;
  border-bottom: 1px solid #eee;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(12px);
}

.mozhi-auth-page-container {
  display: flex;
  height: calc(100vh - 56px);
}

.mozhi-auth-form-side {
  flex: 1.1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.mozhi-auth-brand-side {
  flex: 0.9;
  border-left: 1px solid #eee;
  background: #fafafa;
}
```

Keep the social buttons present but informational only:

```tsx
<button
  className="mozhi-auth-social-btn"
  onClick={() => setInfoMessage("即将支持 Google 登录")}
  type="button"
>
  <GoogleIcon />
</button>
```

- [ ] **Step 4: Re-run the auth-page tests and verify the page matches the new shell contract**

Run:

```powershell
Set-Location 'F:\new_opint\VibeCoding\MOZhi\mozhi-web'
npm run test -- --run src/pages/Auth/AuthPage.test.tsx
```

Expected: PASS for register/login mode structure, right-side brand surface, and register-only terms field.

- [ ] **Step 5: Commit the auth UI recreation**

```bash
git add mozhi-web/src/pages/Auth/index.tsx \
        mozhi-web/src/styles/globals.css \
        mozhi-web/src/layouts/AuthLayout.tsx \
        mozhi-web/src/pages/Auth/AuthPage.test.tsx
git commit -m "feat(web): recreate auth surface from approved reference"
```

### Task 7: Update auth docs and run full-stack verification

**Files:**
- Modify: `README.md`
- Modify: `PROJECT_GUIDE.md`
- Modify: `docs/dev-ops/README.md`
- Modify: `mozhi-web/package.json`

- [ ] **Step 1: Add the missing auth/security documentation**

Document the new runtime contract and env flags:

```md
## Auth session model

- `accessToken` lives in frontend memory only.
- `refreshToken` is issued as `mozhi_refresh_token` `HttpOnly` cookie.
- Local dev may run with `MOZHI_AUTH_COOKIE_SECURE=false`.

## Auth security flags

- `MOZHI_AUTH_COOKIE_SECURE`
- `MOZHI_AUTH_CHALLENGE_PROVIDER`
- `MOZHI_AUTH_CHALLENGE_NOOP_PASS_TOKEN`
```

Ensure the frontend test command is present in `mozhi-web/package.json`:

```json
{
  "scripts": {
    "test": "vitest run",
    "test:watch": "vitest"
  }
}
```

- [ ] **Step 2: Run the backend targeted suites, frontend tests, lint, and build**

Run:

```powershell
Set-Location 'F:\new_opint\VibeCoding\MOZhi\mozhi-backend'
.\mvnw.cmd -q -pl mozhi-app -am -Dtest=AuthHttpIntegrationTest,UserHttpIntegrationTest "-Dsurefire.failIfNoSpecifiedTests=false" test

Set-Location 'F:\new_opint\VibeCoding\MOZhi\mozhi-web'
npm run test -- --run
npm run lint
npm run build
```

Expected:

- backend targeted auth/user suites PASS
- frontend Vitest suite PASS
- ESLint PASS
- Vite production build PASS

- [ ] **Step 3: Run the backend full suite**

Run:

```powershell
Set-Location 'F:\new_opint\VibeCoding\MOZhi\mozhi-backend'
.\mvnw.cmd -q -pl mozhi-app -am test
```

Expected: PASS for the full backend suite with the new auth cookie flow and abuse controls still green.

- [ ] **Step 4: Execute the browser walkthrough and record the expected observations**

Walkthrough checklist:

```text
1. Open /auth?mode=register and confirm the desktop page fits in one screen.
2. Register with a valid 8+ character password and accept the terms checkbox.
3. Confirm login redirects to the original protected target.
4. Refresh /profile and verify silent session recovery via cookie refresh.
5. Trigger repeated bad logins and confirm challenge-required UI appears.
6. Continue failing logins until the temporary lock path returns 429 behavior.
7. Logout and confirm /profile redirects back to /auth.
```

Expected:

- no global shell footer on `/auth`
- no persisted `refreshToken` in localStorage
- refresh cookie visible in the browser cookie jar
- profile route still works after reload when cookie is valid
- challenge and lock states surface cleanly in UI

- [ ] **Step 5: Commit docs and final verification updates**

```bash
git add README.md \
        PROJECT_GUIDE.md \
        docs/dev-ops/README.md \
        mozhi-web/package.json
git commit -m "docs(auth): document cookie session and auth hardening"
```
