package com.rance.galcommunity.community;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import org.junit.jupiter.api.Test;

class CategoryApiIT extends CommunityTestSupport {

    @Test
    void listsCategoryTreeWithCountsAndLastPost() throws Exception {
        long generalId = categoryId("general");
        long topicId = insertTopic(generalId, "最后一帖主题", BASE);
        long postId = insertPost(topicId, 902L, 1, "最后一楼");
        jdbcTemplate.update("""
                UPDATE community_categories SET topic_count = 1, post_count = 3,
                       last_post_id = ?, last_post_at = ? WHERE id = ?
                """, postId, Timestamp.from(BASE), generalId);

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(8))
                .andExpect(jsonPath("$.data[0].slug").value("general"))
                .andExpect(jsonPath("$.data[0].topicCount").value(1))
                .andExpect(jsonPath("$.data[0].postCount").value(3))
                .andExpect(jsonPath("$.data[0].lastPost.topicTitle").value("最后一帖主题"))
                .andExpect(jsonPath("$.data[3].slug").value("resources"))
                .andExpect(jsonPath("$.data[3].children.length()").value(5))
                .andExpect(jsonPath("$.data[3].children[0].minTags").value(1));
    }

    @Test
    void categoriesWithoutPostsHaveNoLastPost() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].lastPost").doesNotExist());
    }
}
