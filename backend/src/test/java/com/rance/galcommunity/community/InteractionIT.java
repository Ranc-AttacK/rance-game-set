package com.rance.galcommunity.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class InteractionIT extends CommunityTestSupport {

    @Test
    void upvoteIsIdempotentAndAdjustsReputation() throws Exception {
        long topicId = insertTopicWithPosts(categoryId("general"), 1);
        long postId = firstPostId(topicId);
        String token = verifiedUserToken("voter@example.com", "Voter");

        mockMvc.perform(post("/api/v1/posts/" + postId + "/upvote")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upvoted").value(true))
                .andExpect(jsonPath("$.data.upvoteCount").value(1));

        mockMvc.perform(post("/api/v1/posts/" + postId + "/upvote")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upvoteCount").value(1));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT upvote_count FROM community_posts WHERE id = ?", Integer.class, postId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reputation FROM community_user_stats WHERE user_id = 901", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_notifications
                WHERE recipient_id = 901 AND type = 'POST_UPVOTE'
                """, Integer.class)).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/posts/" + postId + "/upvote")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upvoted").value(false))
                .andExpect(jsonPath("$.data.upvoteCount").value(0));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reputation FROM community_user_stats WHERE user_id = 901", Integer.class))
                .isZero();
    }

    @Test
    void cannotUpvoteOwnPost() throws Exception {
        String token = verifiedUserToken("selfvote@example.com", "SelfVote");
        long topicId = createTopic(token, "自赞测试主题", categoryId("general"));
        long postId = firstPostId(topicId);

        mockMvc.perform(post("/api/v1/posts/" + postId + "/upvote")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void upvoteRequiresLoginAndVerification() throws Exception {
        long topicId = insertTopicWithPosts(categoryId("general"), 1);
        long postId = firstPostId(topicId);

        mockMvc.perform(post("/api/v1/posts/" + postId + "/upvote"))
                .andExpect(status().isUnauthorized());

        String unverified = unverifiedUserToken("unverified-vote@example.com", "UnverifiedVote");
        mockMvc.perform(post("/api/v1/posts/" + postId + "/upvote")
                        .header("Authorization", "Bearer " + unverified))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40302));
    }

    @Test
    void topicDetailReflectsViewerVoteAndFollowState() throws Exception {
        String token = verifiedUserToken("viewer@example.com", "Viewer");
        long topicId = insertTopicWithPosts(categoryId("general"), 1);
        long postId = firstPostId(topicId);

        mockMvc.perform(post("/api/v1/posts/" + postId + "/upvote")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/topics/" + topicId + "/follow")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.following").value(true))
                .andExpect(jsonPath("$.data.followerCount").value(1));

        mockMvc.perform(get("/api/v1/topics/" + topicId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.following").value(true))
                .andExpect(jsonPath("$.data.followerCount").value(1))
                .andExpect(jsonPath("$.data.posts.items[0].upvoted").value(true))
                .andExpect(jsonPath("$.data.posts.items[0].upvoteCount").value(1));

        mockMvc.perform(get("/api/v1/topics/" + topicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.following").value(false))
                .andExpect(jsonPath("$.data.posts.items[0].upvoted").value(false));
    }

    @Test
    void followIsIdempotentAndCanBeRemoved() throws Exception {
        String token = verifiedUserToken("follower@example.com", "Follower");
        long topicId = insertTopicWithPosts(categoryId("general"), 1);

        mockMvc.perform(post("/api/v1/topics/" + topicId + "/follow")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followerCount").value(1));
        mockMvc.perform(post("/api/v1/topics/" + topicId + "/follow")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followerCount").value(1));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT follower_count FROM community_topics WHERE id = ?", Integer.class, topicId))
                .isEqualTo(1);

        mockMvc.perform(delete("/api/v1/topics/" + topicId + "/follow")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.following").value(false))
                .andExpect(jsonPath("$.data.followerCount").value(0));
    }

    @Test
    void notificationCenterListsAndMarksRead() throws Exception {
        String author = verifiedUserToken("notify-author@example.com", "NotifyAuthor");
        String actor = verifiedUserToken("notify-actor@example.com", "NotifyActor");
        long topicId = createTopic(author, "通知测试主题", categoryId("general"));
        long postId = firstPostId(topicId);

        mockMvc.perform(post("/api/v1/posts/" + postId + "/upvote")
                        .header("Authorization", "Bearer " + actor))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + author))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));

        MvcResult page = mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + author))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("POST_UPVOTE"))
                .andExpect(jsonPath("$.data.items[0].actorNickname").value("NotifyActor"))
                .andExpect(jsonPath("$.data.items[0].read").value(false))
                .andReturn();
        long notificationId = ((Number) JsonPath.read(
                page.getResponse().getContentAsString(), "$.data.items[0].id")).longValue();

        mockMvc.perform(post("/api/v1/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + actor))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + author))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + author))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));
    }

    private long createTopic(String token, String title, long categoryId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/topics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "title": "%s", "content": "正文"}
                                """.formatted(categoryId, title)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }
}
