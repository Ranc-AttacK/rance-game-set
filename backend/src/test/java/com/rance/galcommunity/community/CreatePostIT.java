package com.rance.galcommunity.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CreatePostIT extends CommunityTestSupport {

    @Test
    void replyAllocatesNextFloorAndUpdatesCounters() throws Exception {
        String token = verifiedUserToken("replier@example.com", "Replier");
        long topicId = insertTopicWithPosts(categoryId("general"), 1);

        mockMvc.perform(post("/api/v1/topics/" + topicId + "/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"第一层回复\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.floor").value(2))
                .andExpect(jsonPath("$.data.author.nickname").value("Replier"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT post_count FROM community_topics WHERE id = ?", Integer.class, topicId))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_post_id FROM community_topics WHERE id = ?", Long.class, topicId))
                .isNotNull();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT c.post_count FROM community_categories c
                JOIN community_topics t ON t.category_id = c.id WHERE t.id = ?
                """, Long.class, topicId)).isEqualTo(1L);
    }

    @Test
    void quoteMustBelongToSameTopic() throws Exception {
        String token = verifiedUserToken("quoter@example.com", "Quoter");
        long firstTopic = insertTopicWithPosts(categoryId("general"), 1);
        long secondTopic = insertTopicWithPosts(categoryId("general"), 1);
        long foreignPost = jdbcTemplate.queryForObject(
                "SELECT id FROM community_posts WHERE topic_id = ? AND floor = 1",
                Long.class, secondTopic);

        mockMvc.perform(post("/api/v1/topics/" + firstTopic + "/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"错误引用\",\"replyToPostId\":" + foreignPost + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void replyCanQuotePostInSameTopic() throws Exception {
        String token = verifiedUserToken("quoteok@example.com", "QuoteOk");
        long topicId = insertTopicWithPosts(categoryId("general"), 1);
        long firstPost = jdbcTemplate.queryForObject(
                "SELECT id FROM community_posts WHERE topic_id = ? AND floor = 1",
                Long.class, topicId);

        mockMvc.perform(post("/api/v1/topics/" + topicId + "/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"引用回复\",\"replyToPostId\":" + firstPost + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.replyToPostId").value(firstPost));
    }

    @Test
    void lockedTopicRejectsReply() throws Exception {
        String token = verifiedUserToken("locker@example.com", "Locker");
        long topicId = insertTopicWithPosts(categoryId("general"), 1);
        jdbcTemplate.update("UPDATE community_topics SET locked = b'1' WHERE id = ?", topicId);

        mockMvc.perform(post("/api/v1/topics/" + topicId + "/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"锁定主题回复\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void unverifiedUserCannotReply() throws Exception {
        String token = unverifiedUserToken("unverreply@example.com", "UnverReply");
        long topicId = insertTopicWithPosts(categoryId("general"), 1);

        mockMvc.perform(post("/api/v1/topics/" + topicId + "/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"未验证回复\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40302));
    }

    @Test
    void concurrentRepliesProduceUniqueFloors() throws Exception {
        long topicId = insertTopicWithPosts(categoryId("general"), 1);
        String token = verifiedUserToken("race@example.com", "Race");
        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            futures.add(pool.submit(() -> {
                mockMvc.perform(post("/api/v1/topics/" + topicId + "/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"race\"}"));
                return null;
            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        Integer distinct = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT floor) FROM community_posts WHERE topic_id = ?",
                Integer.class, topicId);
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM community_posts WHERE topic_id = ?", Integer.class, topicId);
        assertThat(distinct).isEqualTo(total).isEqualTo(attempts + 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT post_count FROM community_topics WHERE id = ?", Integer.class, topicId))
                .isEqualTo(attempts + 1);
    }
}
