package com.rance.galcommunity.community.service;

import com.rance.galcommunity.community.domain.Category;
import com.rance.galcommunity.community.domain.Post;
import com.rance.galcommunity.community.domain.Topic;
import com.rance.galcommunity.community.repo.CategoryRepository;
import com.rance.galcommunity.community.repo.PostRepository;
import com.rance.galcommunity.community.repo.TopicRepository;
import com.rance.galcommunity.community.web.dto.CategoryNode;
import com.rance.galcommunity.community.web.dto.LastPostView;
import com.rance.galcommunity.identity.api.IdentityApi;
import com.rance.galcommunity.identity.api.UserSummary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryQueryService {

    private final CategoryRepository categories;
    private final PostRepository posts;
    private final TopicRepository topics;
    private final IdentityApi identity;

    public CategoryQueryService(CategoryRepository categories, PostRepository posts,
                                TopicRepository topics, IdentityApi identity) {
        this.categories = categories;
        this.posts = posts;
        this.topics = topics;
        this.identity = identity;
    }

    @Transactional(readOnly = true)
    public List<CategoryNode> tree() {
        List<Category> all = categories.findByEnabledTrueOrderBySortOrderAscIdAsc();
        Map<Long, LastPostView> lastViews = loadLastPosts(all);
        Map<Long, List<Category>> byParent = all.stream()
                .filter(category -> category.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId, LinkedHashMap::new,
                        Collectors.toList()));
        return all.stream()
                .filter(category -> category.getParentId() == null)
                .map(category -> toNode(category, byParent, lastViews))
                .toList();
    }

    private Map<Long, LastPostView> loadLastPosts(List<Category> all) {
        Set<Long> lastPostIds = all.stream()
                .map(Category::getLastPostId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (lastPostIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Post> postsById = posts.findByIdIn(lastPostIds).stream()
                .collect(Collectors.toMap(Post::getId, post -> post));
        Set<Long> topicIds = postsById.values().stream()
                .map(Post::getTopicId)
                .collect(Collectors.toSet());
        Map<Long, Topic> topicsById = topics.findAllById(topicIds).stream()
                .collect(Collectors.toMap(Topic::getId, topic -> topic));
        Set<Long> authorIds = postsById.values().stream()
                .map(Post::getAuthorId)
                .collect(Collectors.toSet());
        Map<Long, UserSummary> authors = identity.findUsers(authorIds);
        Map<Long, LastPostView> result = new LinkedHashMap<>();
        for (Post post : postsById.values()) {
            Topic topic = topicsById.get(post.getTopicId());
            if (topic == null) {
                continue;
            }
            UserSummary author = authors.get(post.getAuthorId());
            result.put(post.getId(), new LastPostView(post.getId(), topic.getId(), topic.getTitle(),
                    post.getAuthorId(), author != null ? author.nickname() : "已注销用户",
                    post.getCreatedAt()));
        }
        return result;
    }

    private CategoryNode toNode(Category category, Map<Long, List<Category>> byParent,
                                Map<Long, LastPostView> lastViews) {
        List<CategoryNode> children = byParent.getOrDefault(category.getId(), List.of()).stream()
                .map(child -> toNode(child, byParent, lastViews))
                .toList();
        LastPostView lastPost = category.getLastPostId() == null
                ? null
                : lastViews.get(category.getLastPostId());
        return new CategoryNode(category.getId(), category.getParentId(), category.getSlug(),
                category.getName(), category.getDescription(), category.getIcon(),
                category.getColor(), category.getMinTags(), category.getMaxTags(),
                category.getTopicCount(), category.getPostCount(), lastPost, children);
    }
}
