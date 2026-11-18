package com.rance.galcommunity.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "community_categories")
@Getter
@Setter
@NoArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 80)
    private String slug;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(length = 80)
    private String icon;

    @Column(length = 16)
    private String color;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "min_tags", nullable = false)
    private int minTags;

    @Column(name = "max_tags", nullable = false)
    private int maxTags;

    @Column(name = "topic_count", nullable = false)
    private long topicCount;

    @Column(name = "post_count", nullable = false)
    private long postCount;

    @Column(name = "last_post_id")
    private Long lastPostId;

    @Column(name = "last_post_at")
    private Instant lastPostAt;
}
