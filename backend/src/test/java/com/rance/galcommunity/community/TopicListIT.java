package com.rance.galcommunity.community;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class TopicListIT extends CommunityTestSupport {

    @Test
    void categoryTopicsAreCursorPaginatedWithoutDuplicates() throws Exception {
        long generalId = categoryId("general");
        for (int i = 1; i <= 5; i++) {
            insertTopic(generalId, "主题 " + i, BASE.plusSeconds(i));
        }

        MvcResult first = mockMvc.perform(get("/api/v1/categories/general/topics?limit=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].title").value("主题 5"))
                .andExpect(jsonPath("$.data.items[1].title").value("主题 4"))
                .andExpect(jsonPath("$.data.nextCursor").isNotEmpty())
                .andReturn();
        String cursor = JsonPath.read(first.getResponse().getContentAsString(), "$.data.nextCursor");

        MvcResult second = mockMvc.perform(
                        get("/api/v1/categories/general/topics?limit=2&cursor=" + cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value("主题 3"))
                .andExpect(jsonPath("$.data.items[1].title").value("主题 2"))
                .andReturn();
        String next = JsonPath.read(second.getResponse().getContentAsString(), "$.data.nextCursor");

        mockMvc.perform(get("/api/v1/categories/general/topics?limit=2&cursor=" + next))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("主题 1"))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    void pinnedTopicsComeFirst() throws Exception {
        long generalId = categoryId("general");
        insertTopic(generalId, "普通", BASE);
        insertTopic(generalId, "置顶", BASE.minusSeconds(60), true);

        mockMvc.perform(get("/api/v1/categories/general/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value("置顶"))
                .andExpect(jsonPath("$.data.items[0].pinned").value(true));
    }

    @Test
    void latestFeedSpansCategories() throws Exception {
        insertTopic(categoryId("general"), "综合最新", BASE.plusSeconds(1));
        insertTopic(categoryId("news"), "情报最新", BASE.plusSeconds(2));

        mockMvc.perform(get("/api/v1/topics?limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value("情报最新"))
                .andExpect(jsonPath("$.data.items[1].title").value("综合最新"));
    }

    @Test
    void rejectsBadCursor() throws Exception {
        mockMvc.perform(get("/api/v1/categories/general/topics?cursor=bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    void unknownCategoryReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/categories/nope/topics"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }
}
