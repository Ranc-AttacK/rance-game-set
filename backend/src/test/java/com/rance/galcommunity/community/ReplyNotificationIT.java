package com.rance.galcommunity.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class ReplyNotificationIT extends CommunityTestSupport {

    @Test
    void replyNotifiesAuthorQuotedUserAndFollowerOnce() throws Exception {
        String author = verifiedUserToken("reply-author@example.com", "ReplyAuthor");
        String replier = verifiedUserToken("reply-actor@example.com", "ReplyActor");
        String follower = verifiedUserToken("reply-follower@example.com", "ReplyFollower");
        long topicId = createApiTopic(author, categoryId("general"), "通知触发主题");
        long firstPost = firstPostId(topicId);
        long authorId = userIdByEmail("reply-author@example.com");
        long replierId = userIdByEmail("reply-actor@example.com");
        long followerId = userIdByEmail("reply-follower@example.com");

        mockMvc.perform(post("/api/v1/topics/" + topicId + "/follow")
                        .header("Authorization", "Bearer " + follower))
                .andExpect(status().isOk());

        replyApi(replier, topicId, "引用作者的回复", firstPost);

        // 作者同时是主题作者和被引用者：只保留优先级更高的 POST_QUOTE
        assertThat(notificationCount(authorId, "POST_QUOTE")).isEqualTo(1);
        assertThat(notificationCount(authorId, "POST_REPLY")).isZero();
        assertThat(notificationCount(followerId, "TOPIC_FOLLOW_REPLY")).isEqualTo(1);
        assertThat(notificationCount(replierId, "POST_REPLY")
                + notificationCount(replierId, "POST_QUOTE")
                + notificationCount(replierId, "TOPIC_FOLLOW_REPLY")).isZero();

        replyApi(replier, topicId, "普通回复", null);

        assertThat(notificationCount(authorId, "POST_REPLY")).isEqualTo(1);
        assertThat(notificationCount(followerId, "TOPIC_FOLLOW_REPLY")).isEqualTo(2);
    }

    @Test
    void replyWithoutQuoteStillNotifiesAuthor() throws Exception {
        String author = verifiedUserToken("plain-author@example.com", "PlainAuthor");
        String replier = verifiedUserToken("plain-actor@example.com", "PlainActor");
        long topicId = createApiTopic(author, categoryId("general"), "普通回复通知");
        long authorId = userIdByEmail("plain-author@example.com");

        replyApi(replier, topicId, "普通回复", null);

        assertThat(notificationCount(authorId, "POST_REPLY")).isEqualTo(1);
    }
}
