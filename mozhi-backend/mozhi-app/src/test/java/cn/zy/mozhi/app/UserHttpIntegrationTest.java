package cn.zy.mozhi.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.zy.mozhi.domain.auth.adapter.port.IAuthAttemptGuardPort;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = Application.class,
        properties = {
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.url=jdbc:h2:mem:mozhi-user-http;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
        }
)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class UserHttpIntegrationTest {

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
    void should_register_user_and_persist_hashed_password() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest("alice", "alice@mozhi.dev", "Secret123!", "Alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.nickname").value("Alice"));

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM `user` WHERE username = ?",
                String.class,
                "alice"
        );

        assertThat(passwordHash).isNotBlank();
        assertThat(passwordHash).isNotEqualTo("Secret123!");
    }

    @Test
    void should_query_registered_user_profile_by_user_id() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest("alice", "alice@mozhi.dev", "Secret123!", "Alice")))
                .andExpect(status().isOk());

        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE username = ?",
                Long.class,
                "alice"
        );

        String accessToken = loginAndReadAccessToken("alice", "Secret123!");

        mockMvc.perform(get("/api/user/{userId}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.email").value("alice@mozhi.dev"))
                .andExpect(jsonPath("$.data.nickname").value("Alice"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void should_reject_duplicate_username_registration() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest("alice", "alice@mozhi.dev", "Secret123!", "Alice")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest("alice", "alice-2@mozhi.dev", "Secret123!", "Alice Two")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("username already exists"));
    }

    @Test
    void should_reject_invalid_email_registration_request() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest("alice", "alice-at-mozhi.dev", "Secret123!", "Alice")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("email format is invalid"));
    }

    @Test
    void should_reject_registration_when_password_is_shorter_than_eight_chars() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest("shorty", "shorty@mozhi.dev", "1234567", "Shorty")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("password must be at least 8 characters"));
    }

    @Test
    void should_reject_registration_when_password_is_common() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest("commoner", "commoner@mozhi.dev", "password123", "Common")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("password is too weak"));
    }

    @Test
    void should_allow_login_for_existing_bcrypt_hashes_after_argon2_becomes_default() throws Exception {
        String legacyHash = new BCryptPasswordEncoder().encode("LegacyPass8");
        jdbcTemplate.update(
                "INSERT INTO `user` (username, email, password_hash, nickname, avatar_url, bio, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, NULL, NULL, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "legacy",
                "legacy@mozhi.dev",
                legacyHash,
                "Legacy"
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest("legacy", "LegacyPass8")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void should_require_register_challenge_after_three_ip_attempts() throws Exception {
        for (int index = 0; index < 3; index++) {
            mockMvc.perform(post("/api/user/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerRequest("dup", "dup@mozhi.dev", "Secret123!", "Dup")))
                    .andExpect(index == 0 ? status().isOk() : status().isBadRequest());
        }

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest("fresh", "fresh@mozhi.dev", "Secret123!", "Fresh")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0410"));
    }

    @Test
    void should_update_authenticated_user_profile() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest("alice", "alice@mozhi.dev", "Secret123!", "Alice")))
                .andExpect(status().isOk());

        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE username = ?",
                Long.class,
                "alice"
        );

        String accessToken = loginAndReadAccessToken("alice", "Secret123!");

        mockMvc.perform(put("/api/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateProfileRequest("Alice Zhang", "Builds knowledge products.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.nickname").value("Alice Zhang"))
                .andExpect(jsonPath("$.data.bio").value("Builds knowledge products."));

        mockMvc.perform(get("/api/user/{userId}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("Alice Zhang"))
                .andExpect(jsonPath("$.data.bio").value("Builds knowledge products."));
    }

    @Test
    void should_presign_avatar_upload_and_confirm_avatar_url_for_authenticated_user() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest("alice", "alice@mozhi.dev", "Secret123!", "Alice")))
                .andExpect(status().isOk());

        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE username = ?",
                Long.class,
                "alice"
        );

        String accessToken = loginAndReadAccessToken("alice", "Secret123!");

        MvcResult presignResult = mockMvc.perform(post("/api/user/avatar/presign")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(avatarPresignRequest("avatar.png", "image/png")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.objectKey").isNotEmpty())
                .andExpect(jsonPath("$.data.uploadUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.publicUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.httpMethod").value("PUT"))
                .andReturn();

        JsonNode presignData = objectMapper.readTree(presignResult.getResponse().getContentAsString()).path("data");
        String objectKey = presignData.path("objectKey").asText();
        String publicUrl = presignData.path("publicUrl").asText();
        String uploadUrl = presignData.path("uploadUrl").asText();

        assertThat(objectKey).contains("avatars/" + userId + "/");
        assertThat(publicUrl).contains(objectKey);

        byte[] avatarBytes = "fake-png-avatar".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(put(URI.create(uploadUrl).getPath())
                        .contentType(MediaType.IMAGE_PNG)
                        .content(avatarBytes))
                .andExpect(status().isOk());

        mockMvc.perform(get(URI.create(publicUrl).getPath()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(avatarBytes));

        mockMvc.perform(put("/api/user/avatar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmAvatarRequest(objectKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.avatarUrl").value(publicUrl));

        mockMvc.perform(get("/api/user/{userId}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(publicUrl));
    }

    private String loginAndReadAccessToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest(username, password)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return data.path("accessToken").asText();
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
        return String.format(
                "{\"identifier\":\"%s\",\"password\":\"%s\"}",
                identifier,
                password
        );
    }

    private String updateProfileRequest(String nickname, String bio) {
        return String.format(
                "{\"nickname\":\"%s\",\"bio\":\"%s\"}",
                nickname,
                bio
        );
    }

    private String avatarPresignRequest(String fileName, String contentType) {
        return String.format(
                "{\"fileName\":\"%s\",\"contentType\":\"%s\"}",
                fileName,
                contentType
        );
    }

    private String confirmAvatarRequest(String objectKey) {
        return String.format(
                "{\"objectKey\":\"%s\"}",
                objectKey
        );
    }
}
