package com.rance.galcommunity.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class TopicDetailIT extends CommunityTestSupport {

    @Test
    void returnsTopicHeaderAndPostsPaged() throws Exception {
        long topicId = insertTopicWithPosts(categoryId("general"), 25);

        mockMvc.perform(get("/api/v1/topics/" + topicId + "?page=2&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").isNotEmpty())
                .andExpect(jsonPath("$.data.categorySlug").value("general"))
                .andExpect(jsonPath("$.data.posts.items.length()").value(5))
                .andExpect(jsonPath("$.data.posts.items[0].floor").value(21))
                .andExpect(jsonPath("$.data.posts.total").value(25))
                .andExpect(jsonPath("$.data.posts.page").value(2))
                .andExpect(jsonPath("$.data.posts.items[0].author.nickname").isNotEmpty())
                .andExpect(jsonPath("$.data.author.nickname").isNotEmpty());
    }

    @Test
    void incrementsViewCount() throws Exception {
        long topicId = insertTopic(categoryId("general"), "浏览数", BASE);

        mockMvc.perform(get("/api/v1/topics/" + topicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewCount").value(1));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT view_count FROM community_topics WHERE id = ?", Long.class, topicId))
                .isEqualTo(1L);
    }

    @Test
    void deletedOrMissingTopicIs404() throws Exception {
        mockMvc.perform(get("/api/v1/topics/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void pageNumbersBelowOneAreClamped() throws Exception {
        long topicId = insertTopicWithPosts(categoryId("general"), 3);
        mockMvc.perform(get("/api/v1/topics/" + topicId + "?page=-5&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.posts.items[0].floor").value(1));
    }
}
