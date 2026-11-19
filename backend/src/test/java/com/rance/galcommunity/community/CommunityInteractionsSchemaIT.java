package com.rance.galcommunity.community;

import static org.assertj.core.api.Assertions.assertThat;

import com.rance.galcommunity.IntegrationTestBase;
import org.junit.jupiter.api.Test;

class CommunityInteractionsSchemaIT extends IntegrationTestBase {

    @Test
    void interactionTablesAndColumnsExist() {
        assertThat(columnCount("community_topics", "follower_count")).isEqualTo(1);
        assertThat(columnCount("community_posts", "upvote_count")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM community_user_stats", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM community_post_votes", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM community_topic_follows", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_notifications", Integer.class)).isZero();
    }

    private int columnCount(String table, String column) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """, Integer.class, table, column);
    }
}
