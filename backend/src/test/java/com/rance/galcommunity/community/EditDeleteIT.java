package com.rance.galcommunity.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class EditDeleteIT extends CommunityTestSupport {

    @Test
    void authorCanEditPostAndNonAuthorCannot() throws Exception {
        String author = verifiedUserToken("edit-author@example.com", "EditAuthor");
        String other = verifiedUserToken("edit-other@example.com", "EditOther");
        long topicId = createApiTopic(author, categoryId("general"), "编辑楼层测试");
        long postId = firstPostId(topicId);

        mockMvc.perform(patch("/api/v1/posts/" + postId)
                        .header("Authorization", "Bearer " + author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"修改后的正文\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("修改后的正文"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT edit_count FROM community_posts WHERE id = ?", Integer.class, postId))
                .isEqualTo(1);

        mockMvc.perform(patch("/api/v1/posts/" + postId)
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"越权修改\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void authorCanEditTopicTitle() throws Exception {
        String author = verifiedUserToken("title-author@example.com", "TitleAuthor");
        String other = verifiedUserToken("title-other@example.com", "TitleOther");
        long topicId = createApiTopic(author, categoryId("general"), "原标题测试");

        mockMvc.perform(patch("/api/v1/topics/" + topicId)
                        .header("Authorization", "Bearer " + author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"修改后的标题\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("修改后的标题"));

        mockMvc.perform(patch("/api/v1/topics/" + topicId)
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"越权标题修改\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingReplyRecomputesTopicAndCategoryCounters() throws Exception {
        String author = verifiedUserToken("del-author@example.com", "DelAuthor");
        String replier = verifiedUserToken("del-replier@example.com", "DelReplier");
        long generalId = categoryId("general");
        long topicId = createApiTopic(author, generalId, "删除楼层测试");
        long replyId = replyApi(replier, topicId, "会被删除的回复", null);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT post_count FROM community_topics WHERE id = ?", Integer.class, topicId))
                .isEqualTo(2);

        mockMvc.perform(delete("/api/v1/posts/" + replyId)
                        .header("Authorization", "Bearer " + replier))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT post_count FROM community_topics WHERE id = ?", Integer.class, topicId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT post_count FROM community_categories WHERE id = ?", Long.class, generalId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_post_id FROM community_topics WHERE id = ?", Long.class, topicId))
                .isEqualTo(firstPostId(topicId));

        mockMvc.perform(get("/api/v1/topics/" + topicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.posts.total").value(1));
    }

    @Test
    void deletingTopicRecomputesCategoryCountersAndHidesIt() throws Exception {
        String author = verifiedUserToken("topic-del@example.com", "TopicDel");
        String replier = verifiedUserToken("topic-del-replier@example.com", "TopicDelReplier");
        long generalId = categoryId("general");
        long topicId = createApiTopic(author, generalId, "删除主题测试");
        replyApi(replier, topicId, "回复内容", null);

        mockMvc.perform(delete("/api/v1/topics/" + topicId)
                        .header("Authorization", "Bearer " + author))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/topics/" + topicId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/categories/general/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT topic_count FROM community_categories WHERE id = ?", Long.class, generalId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT post_count FROM community_categories WHERE id = ?", Long.class, generalId))
                .isZero();

        long authorId = userIdByEmail("topic-del@example.com");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT topic_count FROM community_user_stats WHERE user_id = ?",
                Integer.class, authorId)).isZero();
    }

    @Test
    void nonAuthorCannotDeleteTopic() throws Exception {
        String author = verifiedUserToken("del-owner@example.com", "DelOwner");
        String other = verifiedUserToken("del-intruder@example.com", "DelIntruder");
        long topicId = createApiTopic(author, categoryId("general"), "越权删除主题");

        mockMvc.perform(delete("/api/v1/topics/" + topicId)
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden());
    }
}
