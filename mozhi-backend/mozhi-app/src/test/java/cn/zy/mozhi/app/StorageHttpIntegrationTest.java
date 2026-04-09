package cn.zy.mozhi.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.zy.mozhi.domain.auth.adapter.port.IAuthAttemptGuardPort;
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

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = Application.class,
        properties = {
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.url=jdbc:h2:mem:mozhi-storage-http;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class StorageHttpIntegrationTest {

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
        deleteIfExists("media_ref");
        deleteIfExists("note");
        deleteIfExists("draft");
        jdbcTemplate.execute("DELETE FROM `user`");
        authAttemptGuardPort.clearAll();
    }

    @Test
    void should_presign_upload_and_complete_draft_media_loop() throws Exception {
        String username = "storageuser";
        String accessToken = registerAndLogin(username, "storageuser@mozhi.dev", "Secret123!", "StorageUser");
        long userId = queryUserId(username);
        long draftId = createDraft(accessToken, "Storage draft", "Body");
        byte[] mediaBytes = "fake-image-bytes".getBytes();

        MvcResult presignResult = mockMvc.perform(post("/api/storage/presign")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(storagePresignRequest(draftId, "cover.png", "image/png", "IMAGE", (long) mediaBytes.length)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadTicket").isNotEmpty())
                .andExpect(jsonPath("$.data.objectKey").isNotEmpty())
                .andReturn();

        JsonNode presignData = objectMapper.readTree(presignResult.getResponse().getContentAsString()).path("data");
        String objectKey = presignData.path("objectKey").asText();
        String uploadUrl = presignData.path("uploadUrl").asText();
        String uploadTicket = presignData.path("uploadTicket").asText();

        org.assertj.core.api.Assertions.assertThat(objectKey).contains("drafts/" + userId + "/" + draftId + "/");

        mockMvc.perform(put(URI.create(uploadUrl).getPath())
                        .contentType(MediaType.IMAGE_PNG)
                        .content(mediaBytes))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/content/drafts/{draftId}/media/confirm", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmDraftMediaRequest(objectKey, uploadTicket, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].mediaType").value("IMAGE"));

        mockMvc.perform(get("/api/content/drafts/{draftId}/media", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].objectKey").value(objectKey))
                .andExpect(jsonPath("$.data.items[0].uploadStatus").value("CONFIRMED"));
    }

    private void deleteIfExists(String tableName) {
        Integer tableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = ?",
                Integer.class,
                tableName
        );
        if (tableCount != null && tableCount > 0) {
            jdbcTemplate.execute("DELETE FROM `" + tableName + "`");
        }
    }

    private String registerAndLogin(String username, String email, String password, String nickname) throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest(username, email, password, nickname)))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest(username, password)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
    }

    private long queryUserId(String username) {
        return jdbcTemplate.queryForObject("SELECT id FROM `user` WHERE username = ?", Long.class, username);
    }

    private long createDraft(String accessToken, String title, String content) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/content/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequest(title, content)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("draftId").asLong();
    }

    private String registerRequest(String username, String email, String password, String nickname) {
        return writeJson(new LinkedHashMap<>(Map.of(
                "username", username,
                "email", email,
                "password", password,
                "nickname", nickname
        )));
    }

    private String loginRequest(String identifier, String password) {
        return writeJson(new LinkedHashMap<>(Map.of(
                "identifier", identifier,
                "password", password
        )));
    }

    private String createDraftRequest(String title, String content) {
        return writeJson(new LinkedHashMap<>(Map.of(
                "title", title,
                "content", content
        )));
    }

    private String storagePresignRequest(long draftId,
                                         String fileName,
                                         String contentType,
                                         String mediaType,
                                         long declaredSizeBytes) {
        return writeJson(new LinkedHashMap<>(Map.of(
                "purpose", "DRAFT_MEDIA",
                "draftId", draftId,
                "fileName", fileName,
                "contentType", contentType,
                "mediaType", mediaType,
                "declaredSizeBytes", declaredSizeBytes
        )));
    }

    private String confirmDraftMediaRequest(String objectKey, String uploadTicket, int sortOrder) {
        return writeJson(new LinkedHashMap<>(Map.of(
                "objectKey", objectKey,
                "uploadTicket", uploadTicket,
                "sortOrder", sortOrder
        )));
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to serialize test payload", exception);
        }
    }
}
