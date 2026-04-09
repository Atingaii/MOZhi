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

import java.util.LinkedHashMap;
import java.util.Map;

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
    void should_create_list_update_and_delete_own_editable_draft() throws Exception {
        String accessToken = registerAndLogin("author", "author@mozhi.dev", "Secret123!", "Author");

        MvcResult createResult = mockMvc.perform(post("/api/content/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequest("First draft", "Draft body")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn();

        long draftId = readDraftId(createResult);

        mockMvc.perform(get("/api/content/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].draftId").value(draftId))
                .andExpect(jsonPath("$.data.items[0].version").value(0));

        mockMvc.perform(get("/api/content/drafts/{draftId}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftId").value(draftId))
                .andExpect(jsonPath("$.data.title").value("First draft"))
                .andExpect(jsonPath("$.data.content").value("Draft body"));

        mockMvc.perform(put("/api/content/drafts/{draftId}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateDraftRequest("Updated title", "Updated body", 0L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated title"))
                .andExpect(jsonPath("$.data.content").value("Updated body"))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(delete("/api/content/drafts/{draftId}", draftId)
                        .queryParam("expectedVersion", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/content/drafts/{draftId}", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
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
                        .content(updateDraftRequest("Malicious overwrite", "Should fail", 0L)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/content/drafts/{draftId}/status", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionDraftStatusRequest("PUBLISHED", 0L)))
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
                        .content(transitionDraftStatusRequest("PENDING_REVIEW", 0L)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/content/drafts/{draftId}/status", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionDraftStatusRequest("PUBLISHED", 1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(delete("/api/content/drafts/{draftId}", draftId)
                        .queryParam("expectedVersion", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void should_reject_deleting_pending_review_and_archived_drafts() throws Exception {
        String accessToken = registerAndLogin("lifecycle", "lifecycle@mozhi.dev", "Secret123!", "Lifecycle");

        DraftSnapshot pendingReviewDraft = createDraft(accessToken, "Pending review", "Body");
        mockMvc.perform(post("/api/content/drafts/{draftId}/status", pendingReviewDraft.draftId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionDraftStatusRequest("PENDING_REVIEW", pendingReviewDraft.version())))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/content/drafts/{draftId}", pendingReviewDraft.draftId())
                        .queryParam("expectedVersion", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("draft in current status cannot be deleted"));

        DraftSnapshot archivedDraft = createDraft(accessToken, "Archived draft", "Body");
        mockMvc.perform(post("/api/content/drafts/{draftId}/status", archivedDraft.draftId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionDraftStatusRequest("ARCHIVED", archivedDraft.version())))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/content/drafts/{draftId}", archivedDraft.draftId())
                        .queryParam("expectedVersion", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("draft in current status cannot be deleted"));
    }

    @Test
    void should_reject_content_updates_for_frozen_states() throws Exception {
        String accessToken = registerAndLogin("frozen", "frozen@mozhi.dev", "Secret123!", "Frozen");
        DraftSnapshot draftSnapshot = createDraft(accessToken, "Frozen draft", "Body");

        mockMvc.perform(post("/api/content/drafts/{draftId}/status", draftSnapshot.draftId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionDraftStatusRequest("PENDING_REVIEW", 0L)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/content/drafts/{draftId}", draftSnapshot.draftId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateDraftRequest("Should reject", "Frozen body", 1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void should_return_conflict_for_stale_mutations() throws Exception {
        String accessToken = registerAndLogin("versioned", "versioned@mozhi.dev", "Secret123!", "Versioned");
        DraftSnapshot draftSnapshot = createDraft(accessToken, "Versioned draft", "Body");

        mockMvc.perform(put("/api/content/drafts/{draftId}", draftSnapshot.draftId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateDraftRequest("Fresh write", "Fresh body", 0L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(post("/api/content/drafts/{draftId}/status", draftSnapshot.draftId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionDraftStatusRequest("PENDING_REVIEW", 0L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("A0409"));

        mockMvc.perform(delete("/api/content/drafts/{draftId}", draftSnapshot.draftId())
                        .queryParam("expectedVersion", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("A0409"));
    }

    @Test
    void should_page_and_filter_draft_list() throws Exception {
        String accessToken = registerAndLogin("pager", "pager@mozhi.dev", "Secret123!", "Pager");
        createDraft(accessToken, "draft-1", "body-1");
        DraftSnapshot filteredDraft = createDraft(accessToken, "draft-2", "body-2");

        mockMvc.perform(post("/api/content/drafts/{draftId}/status", filteredDraft.draftId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionDraftStatusRequest("PENDING_REVIEW", 0L)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/content/drafts")
                        .queryParam("page", "1")
                        .queryParam("pageSize", "1")
                        .queryParam("status", "PENDING_REVIEW")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(1))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING_REVIEW"));
    }

    @Test
    void should_require_authentication_for_draft_endpoints() throws Exception {
        mockMvc.perform(get("/api/content/drafts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A0401"));
    }

    @Test
    void should_reject_invalid_draft_requests_at_http_boundary() throws Exception {
        String accessToken = registerAndLogin("validator", "validator@mozhi.dev", "Secret123!", "Validator");
        DraftSnapshot draftSnapshot = createDraft(accessToken, "Valid draft", "Body");

        mockMvc.perform(post("/api/content/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequest(" ", "Body")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("title must not be blank"));

        mockMvc.perform(put("/api/content/drafts/{draftId}", draftSnapshot.draftId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateDraftRequest("Updated", "Body", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("expectedVersion must not be null"));

        mockMvc.perform(post("/api/content/drafts/{draftId}/status", draftSnapshot.draftId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionDraftStatusRequest(" ", 0L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("targetStatus must not be blank"));
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

    private DraftSnapshot createDraft(String accessToken, String title, String content) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/content/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createDraftRequest(title, content)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data");
        return new DraftSnapshot(data.path("draftId").asLong(), data.path("version").asLong());
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

    private String updateDraftRequest(String title, String content, Long expectedVersion) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("content", content);
        payload.put("expectedVersion", expectedVersion);
        return writeJson(payload);
    }

    private String transitionDraftStatusRequest(String targetStatus, long expectedVersion) {
        return writeJson(new LinkedHashMap<>(Map.of(
                "targetStatus", targetStatus,
                "expectedVersion", expectedVersion
        )));
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to serialize test payload", exception);
        }
    }

    private record DraftSnapshot(long draftId, long version) {
    }
}
