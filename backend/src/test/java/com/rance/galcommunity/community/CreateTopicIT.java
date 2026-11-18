package com.rance.galcommunity.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class CreateTopicIT extends CommunityTestSupport {

    @Test
    void verifiedUserCreatesTopicWithFirstFloorAndCounters() throws Exception {
        long generalId = categoryId("general");
        String token = verifiedUserToken("poster@example.com", "Poster");

        MvcResult result = mockMvc.perform(post("/api/v1/topics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "title": "我的第一个主题",
                                 "content": "正文内容", "tags": ["汉化", "补丁"]}
                                """.formatted(generalId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("我的第一个主题"))
                .andExpect(jsonPath("$.data.tags.length()").value(2))
                .andReturn();

        long topicId = ((Number) JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.id")).longValue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT floor FROM community_posts WHERE topic_id = ?", Integer.class, topicId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT topic_count FROM community_categories WHERE id = ?", Long.class, generalId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT post_count FROM community_categories WHERE id = ?", Long.class, generalId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_post_id FROM community_categories WHERE id = ?", Long.class, generalId))
                .isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM community_topic_tags WHERE topic_id = ?", Integer.class, topicId))
                .isEqualTo(2);
    }

    @Test
    void unverifiedUserCannotCreateTopic() throws Exception {
        String token = unverifiedUserToken("unverified@example.com", "Unverified");
        mockMvc.perform(post("/api/v1/topics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "title": "未验证主题", "content": "正文"}
                                """.formatted(categoryId("general"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40302));
    }

    @Test
    void enforcesCategoryTagRules() throws Exception {
        String token = verifiedUserToken("tagger@example.com", "Tagger");
        mockMvc.perform(post("/api/v1/topics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "title": "无标签主题", "content": "正文"}
                                """.formatted(categoryId("resources"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void rejectsTooShortTitle() throws Exception {
        String token = verifiedUserToken("short@example.com", "Short");
        mockMvc.perform(post("/api/v1/topics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "title": "短", "content": "正文"}
                                """.formatted(categoryId("general"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void unknownCategoryReturns404() throws Exception {
        String token = verifiedUserToken("nocat@example.com", "NoCat");
        mockMvc.perform(post("/api/v1/topics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": 999999, "title": "不存在的分区", "content": "正文"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void duplicateTagsAreDeduplicated() throws Exception {
        long generalId = categoryId("general");
        String token = verifiedUserToken("duptag@example.com", "DupTag");
        MvcResult result = mockMvc.perform(post("/api/v1/topics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": %d, "title": "重复标签主题", "content": "正文",
                                 "tags": ["汉化", "汉化", " 补丁 "]}
                                """.formatted(generalId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tags.length()").value(2))
                .andReturn();
        long topicId = ((Number) JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.id")).longValue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM community_topic_tags WHERE topic_id = ?", Integer.class, topicId))
                .isEqualTo(2);
    }
}
