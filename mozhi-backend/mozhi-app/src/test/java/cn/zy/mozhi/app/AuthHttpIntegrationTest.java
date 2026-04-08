package cn.zy.mozhi.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.zy.mozhi.domain.auth.adapter.port.IAuthAttemptGuardPort;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = Application.class,
        properties = {
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.url=jdbc:h2:mem:mozhi-auth-http;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "mozhi.auth.challenge.provider=test",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
        }
)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthHttpIntegrationTest {

    private static final String REFRESH_COOKIE_NAME = "mozhi_refresh_token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IAuthAttemptGuardPort authAttemptGuardPort;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM `user`");
        authAttemptGuardPort.clearAll();
    }

    @Test
    void should_require_access_token_for_user_profile_and_allow_access_after_login() throws Exception {
        registerUser("alice", "alice@mozhi.dev", "Secret123!", "Alice");

        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE username = ?",
                Long.class,
                "alice"
        );

        mockMvc.perform(get("/api/user/{userId}", userId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0401"));

        String accessToken = loginAndReadField("alice", "Secret123!", "accessToken");

        mockMvc.perform(get("/api/user/{userId}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.username").value("alice"));
    }

    @Test
    void should_set_refresh_cookie_on_login_and_hide_refresh_token_from_body() throws Exception {
        registerUser("alice", "alice@mozhi.dev", "Secret123!", "Alice");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest("alice", "Secret123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.accessTokenExpiresAt").isNotEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.SET_COOKIE, containsString(REFRESH_COOKIE_NAME + "=")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")));
    }

    @Test
    void should_rotate_refresh_cookie_and_reject_reuse_of_old_refresh_token() throws Exception {
        registerUser("alice", "alice@mozhi.dev", "Secret123!", "Alice");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest("alice", "Secret123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        Cookie oldRefreshCookie = refreshCookie(loginResult);
        String oldRefreshToken = oldRefreshCookie.getValue();

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(oldRefreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.SET_COOKIE, containsString(REFRESH_COOKIE_NAME + "=")))
                .andReturn();

        Cookie rotatedRefreshCookie = refreshCookie(refreshResult);
        String rotatedRefreshToken = rotatedRefreshCookie.getValue();
        assertThat(rotatedRefreshToken).isNotEqualTo(oldRefreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(oldRefreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0401"));
    }

    @Test
    void should_reject_refresh_without_cookie_instead_of_returning_server_error() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0401"));
    }

    @Test
    void should_logout_current_session_and_reject_reuse_of_access_and_refresh_tokens() throws Exception {
        registerUser("alice", "alice@mozhi.dev", "Secret123!", "Alice");

        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE username = ?",
                Long.class,
                "alice"
        );

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest("alice", "Secret123!")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginData = readData(loginResult);
        String accessToken = loginData.path("accessToken").asText();
        Cookie refreshCookie = refreshCookie(loginResult);

        mockMvc.perform(get("/api/user/{userId}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        mockMvc.perform(get("/api/user/{userId}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0401"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0401"));
    }

    @Test
    void should_logout_all_sessions_and_reject_tokens_issued_before_revocation() throws Exception {
        registerUser("alice", "alice@mozhi.dev", "Secret123!", "Alice");

        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE username = ?",
                Long.class,
                "alice"
        );

        MvcResult firstLoginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest("alice", "Secret123!")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode firstLoginData = readData(firstLoginResult);
        String firstAccessToken = firstLoginData.path("accessToken").asText();
        Cookie firstRefreshCookie = refreshCookie(firstLoginResult);

        MvcResult secondLoginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest("alice", "Secret123!")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode secondLoginData = readData(secondLoginResult);
        String secondAccessToken = secondLoginData.path("accessToken").asText();
        Cookie secondRefreshCookie = refreshCookie(secondLoginResult);

        mockMvc.perform(post("/api/auth/logout/all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/user/{userId}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0401"));

        mockMvc.perform(get("/api/user/{userId}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0401"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(firstRefreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0401"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(secondRefreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0401"));

        String newAccessToken = loginAndReadField("alice", "Secret123!", "accessToken");

        mockMvc.perform(get("/api/user/{userId}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void should_reject_login_with_wrong_password() throws Exception {
        registerUser("alice", "alice@mozhi.dev", "Secret123!", "Alice");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest("alice", "WrongPass123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0401"));
    }

    @Test
    void should_allow_login_with_email_identifier() throws Exception {
        registerUser("alice", "alice@mozhi.dev", "Secret123!", "Alice");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest("alice@mozhi.dev", "Secret123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void should_require_challenge_after_five_failed_login_attempts() throws Exception {
        registerUser("alice", "alice@mozhi.dev", "Secret123!", "Alice");

        for (int index = 0; index < 5; index++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequest("alice", "WrongPass8")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest("alice", "Secret123!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0410"));
    }

    @Test
    void should_temporarily_lock_login_after_ten_failed_attempts() throws Exception {
        registerUser("alice", "alice@mozhi.dev", "Secret123!", "Alice");

        for (int index = 0; index < 10; index++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequest("alice", "WrongPass8", "test-pass-token")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest("alice", "Secret123!", "test-pass-token")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0429"));
    }

    private void registerUser(String username, String email, String password, String nickname) throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest(username, email, password, nickname)))
                .andExpect(status().isOk());
    }

    private String loginAndReadField(String username, String password, String fieldName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest(username, password)))
                .andExpect(status().isOk())
                .andReturn();

        return readData(result).path(fieldName).asText();
    }

    private JsonNode readData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private Cookie refreshCookie(MvcResult result) {
        String headerValue = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        return new Cookie(REFRESH_COOKIE_NAME, extractCookieValue(headerValue, REFRESH_COOKIE_NAME));
    }

    private String registerRequest(String username, String email, String password, String nickname) {
        return String.format(
                "{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"%s\"}",
                username,
                email,
                password,
                nickname
        );
    }

    private String loginRequest(String identifier, String password) {
        return loginRequest(identifier, password, null);
    }

    private String loginRequest(String identifier, String password, String challengeToken) {
        if (challengeToken == null) {
            return String.format(
                    "{\"identifier\":\"%s\",\"password\":\"%s\"}",
                    identifier,
                    password
            );
        }
        return String.format(
                "{\"identifier\":\"%s\",\"password\":\"%s\",\"challengeToken\":\"%s\"}",
                identifier,
                password,
                challengeToken
        );
    }

    private String extractCookieValue(String setCookieHeader, String cookieName) {
        assertThat(setCookieHeader).isNotBlank();
        String prefix = cookieName + "=";
        int startIndex = setCookieHeader.indexOf(prefix);
        assertThat(startIndex).isGreaterThanOrEqualTo(0);
        int valueStart = startIndex + prefix.length();
        int valueEnd = setCookieHeader.indexOf(';', valueStart);
        return valueEnd >= 0 ? setCookieHeader.substring(valueStart, valueEnd) : setCookieHeader.substring(valueStart);
    }
}
