package com.rance.galcommunity.community;

import static org.assertj.core.api.Assertions.assertThat;

import com.rance.galcommunity.IntegrationTestBase;
import org.junit.jupiter.api.Test;

class CommunitySchemaIT extends IntegrationTestBase {

    @Test
    void communityTablesAndSeedCategoriesExist() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM community_categories", Integer.class)).isEqualTo(13);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM community_categories WHERE parent_id IS NOT NULL",
                Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM community_categories WHERE slug='general'", String.class))
                .isEqualTo("综合讨论");
    }
}
