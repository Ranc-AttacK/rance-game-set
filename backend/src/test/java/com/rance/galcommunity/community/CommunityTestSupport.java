package com.rance.galcommunity.community;

import com.jayway.jsonpath.JsonPath;
import com.rance.galcommunity.IntegrationTestBase;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

abstract class CommunityTestSupport extends IntegrationTestBase {

    static final Instant BASE = Instant.parse("2026-11-18T06:00:00Z");

    protected long categoryId(String slug) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM community_categories WHERE slug = ?", Long.class, slug);
    }

    protected long insertTopic(long categoryId, String title, Instant lastPostAt) {
        return insertTopic(categoryId, title, lastPostAt, false);
    }

    protected long insertTopic(long categoryId, String title, Instant lastPostAt, boolean pinned) {
        insertTopicRow(categoryId, 900L, title, lastPostAt, pinned);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM community_topics WHERE title = ?", Long.class, title);
    }

    protected long insertTopicWithPosts(long categoryId, int postCount) {
        long topicId = insertTopic(categoryId, "主题标题-" + System.nanoTime(), BASE);
        for (int floor = 1; floor <= postCount; floor++) {
            insertPost(topicId, 901L, floor, "楼层 " + floor);
        }
        jdbcTemplate.update("""
                UPDATE community_topics SET post_count = ?, last_post_at = ?,
                       last_post_id = (SELECT MAX(id) FROM community_posts WHERE topic_id = ?)
                WHERE id = ?
                """, postCount, Timestamp.from(BASE), topicId, topicId);
        return topicId;
    }

    protected long insertPost(long topicId, long authorId, int floor, String content) {
        jdbcTemplate.update("""
                INSERT INTO community_posts
                    (topic_id, author_id, content, floor, visibility, edit_count,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, 'PUBLIC', 0, ?, ?)
                """, topicId, authorId, content, floor, Timestamp.from(BASE),
                Timestamp.from(BASE));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM community_posts WHERE topic_id = ? AND floor = ?",
                Long.class, topicId, floor);
    }

    protected String verifiedUserToken(String email, String nickname) throws Exception {
        String verification = registerAndGetVerificationToken(email, nickname, "secret123");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", verification))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk());
        return loginToken(email);
    }

    protected String unverifiedUserToken(String email, String nickname) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "nickname", nickname, "password", "secret123"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isCreated());
        return loginToken(email);
    }

    protected long createApiTopic(String token, long categoryId, String title) throws Exception {
        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/topics")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "categoryId", categoryId,
                                        "title", title,
                                        "content", "主题正文"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    protected long replyApi(String token, long topicId, String content, Long replyToPostId)
            throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("content", content);
        if (replyToPostId != null) {
            body.put("replyToPostId", replyToPostId);
        }
        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/topics/" + topicId + "/posts")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    protected long firstPostId(long topicId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM community_posts WHERE topic_id = ? AND floor = 1",
                Long.class, topicId);
    }

    protected long userIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM user_accounts WHERE email = ?", Long.class, email);
    }

    protected int notificationCount(long recipientId, String type) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_notifications WHERE recipient_id = ? AND type = ?
                """, Integer.class, recipientId, type);
    }

    private String loginToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of("email", email, "password", "secret123"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private void insertTopicRow(long categoryId, long authorId, String title,
                                Instant lastPostAt, boolean pinned) {
        jdbcTemplate.update("""
                INSERT INTO community_topics
                    (category_id, author_id, kind, title, pinned, locked, visibility,
                     post_count, view_count, last_post_at, created_at, updated_at)
                VALUES (?, ?, 'DISCUSSION', ?, ?, b'0', 'PUBLIC', 1, 0, ?, ?, ?)
                """, categoryId, authorId, title, pinned, Timestamp.from(lastPostAt),
                Timestamp.from(lastPostAt), Timestamp.from(lastPostAt));
    }
}
