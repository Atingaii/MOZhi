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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = Application.class,
        properties = {
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.url=jdbc:h2:mem:mozhi-draft-http;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class DraftHttpIntegrationTest {

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
    void should_create_list_update_transition_and_delete_own_draft() throws Exception {
        String accessToken = registerAndLogin("author", "author@mozhi.dev", "Secret123!", "Author");

        MvcResult createResult = mockMvc.perform(post("/api/content/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequest("First draft", "Draft body")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();

        long draftId = readDraftId(createResult);

        mockMvc.perform(get("/api/content/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].draftId").value(draftId));

        mockMvc.perform(get("/api/content/drafts/{draftId}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftId").value(draftId))
                .andExpect(jsonPath("$.data.title").value("First draft"))
                .andExpect(jsonPath("$.data.content").value("Draft body"));

        mockMvc.perform(put("/api/content/drafts/{draftId}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateDraftRequest("Updated title", "Updated body")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated title"))
                .andExpect(jsonPath("$.data.content").value("Updated body"));

        mockMvc.perform(post("/api/content/drafts/{draftId}/status", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionDraftStatusRequest("PENDING_REVIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));

        mockMvc.perform(delete("/api/content/drafts/{draftId}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void should_hide_foreign_owned_draft_and_reject_illegal_transitions() throws Exception {
        String ownerToken = registerAndLogin("owner", "owner@mozhi.dev", "Secret123!", "Owner");
        String strangerToken = registerAndLogin("stranger", "stranger@mozhi.dev", "Secret123!", "Stranger");

        MvcResult createResult = mockMvc.perform(post("/api/content/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequest("Private draft", "Private body")))
                .andExpect(status().isOk())
                .andReturn();

        long draftId = readDraftId(createResult);

        mockMvc.perform(get("/api/content/drafts/{draftId}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/content/drafts/{draftId}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateDraftRequest("Malicious overwrite", "Should fail")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/content/drafts/{draftId}/status", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionDraftStatusRequest("PUBLISHED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void should_reject_deleting_published_draft() throws Exception {
        String accessToken = registerAndLogin("publisher", "publisher@mozhi.dev", "Secret123!", "Publisher");

        MvcResult createResult = mockMvc.perform(post("/api/content/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequest("Ready to publish", "Publishing body")))
                .andExpect(status().isOk())
                .andReturn();

        long draftId = readDraftId(createResult);

        mockMvc.perform(post("/api/content/drafts/{draftId}/status", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionDraftStatusRequest("PENDING_REVIEW")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/content/drafts/{draftId}/status", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionDraftStatusRequest("PUBLISHED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(delete("/api/content/drafts/{draftId}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
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

    private long readDraftId(MvcResult createResult) throws Exception {
        JsonNode data = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data");
        return data.path("draftId").asLong();
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

    private String createDraftRequest(String title, String content) {
        return String.format(
                "{\"title\":\"%s\",\"content\":\"%s\"}",
                title,
                content
        );
    }

    private String updateDraftRequest(String title, String content) {
        return createDraftRequest(title, content);
    }

    private String transitionDraftStatusRequest(String targetStatus) {
        return String.format(
                "{\"targetStatus\":\"%s\"}",
                targetStatus
        );
    }
}
