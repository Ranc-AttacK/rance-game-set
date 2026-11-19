package com.rance.galcommunity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("gal")
            .withUsername("gal")
            .withPassword("galpass");

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    protected org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @org.springframework.boot.test.mock.mockito.MockBean
    protected com.rance.galcommunity.identity.mail.EmailSender emailSender;

    @Autowired
    protected com.rance.galcommunity.identity.service.EmailOutboxProcessor emailOutboxProcessor;

    @org.junit.jupiter.api.BeforeEach
    void resetState() {
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        // Later tasks add these tables; the per-statement try/catch keeps Task 1 green before they exist.
        truncate("user_notifications");
        truncate("community_topic_follows");
        truncate("community_post_votes");
        truncate("community_user_stats");
        truncate("community_topic_tags");
        truncate("community_tags");
        truncate("community_posts");
        truncate("community_topics");
        resetCategoryCounters();
        truncate("event_publication");
        truncate("user_email_outbox");
        truncate("user_password_resets");
        truncate("user_email_verifications");
        truncate("user_refresh_tokens");
        truncate("user_accounts");
    }

    private void truncate(String table) {
        try {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        } catch (Exception ignored) {
            // Table not present yet in this task's schema.
        }
    }

    private void resetCategoryCounters() {
        try {
            jdbcTemplate.update("""
                    UPDATE community_categories
                    SET topic_count = 0, post_count = 0, last_post_id = NULL, last_post_at = NULL
                    """);
        } catch (Exception ignored) {
            // Table not present yet in this task's schema.
        }
    }

    protected String registerAndGetVerificationToken(String email, String nickname, String password)
            throws Exception {
        org.mockito.Mockito.clearInvocations(emailSender);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email, "nickname", nickname, "password", password))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isCreated());

        emailOutboxProcessor.processDue(java.time.Instant.now());

        org.mockito.ArgumentCaptor<String> body = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(emailSender, org.mockito.Mockito.atLeastOnce())
                .send(org.mockito.ArgumentMatchers.eq(email),
                        org.mockito.ArgumentMatchers.anyString(), body.capture());
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("token=([A-Za-z0-9_-]+)").matcher(body.getValue());
        org.assertj.core.api.Assertions.assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
