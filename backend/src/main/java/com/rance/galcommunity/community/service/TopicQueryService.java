package com.rance.galcommunity.community.service;

import com.rance.galcommunity.community.domain.Category;
import com.rance.galcommunity.community.domain.Post;
import com.rance.galcommunity.community.domain.Tag;
import com.rance.galcommunity.community.domain.Topic;
import com.rance.galcommunity.community.domain.TopicFollowId;
import com.rance.galcommunity.community.repo.CategoryRepository;
import com.rance.galcommunity.community.repo.PostRepository;
import com.rance.galcommunity.community.repo.PostVoteRepository;
import com.rance.galcommunity.community.repo.TagRepository;
import com.rance.galcommunity.community.repo.TopicFollowRepository;
import com.rance.galcommunity.community.repo.TopicRepository;
import com.rance.galcommunity.community.repo.TopicTagRepository;
import com.rance.galcommunity.community.web.TopicCursor;
import com.rance.galcommunity.community.web.dto.PostView;
import com.rance.galcommunity.community.web.dto.TagView;
import com.rance.galcommunity.community.web.dto.TopicDetailResponse;
import com.rance.galcommunity.community.web.dto.TopicListItem;
import com.rance.galcommunity.community.web.dto.UserBrief;
import com.rance.galcommunity.identity.api.IdentityApi;
import com.rance.galcommunity.identity.api.UserSummary;
import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.CursorPage;
import com.rance.galcommunity.shared.api.ErrorCode;
import com.rance.galcommunity.shared.api.PageResponse;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopicQueryService {

    private final TopicRepository topics;
    private final PostRepository posts;
    private final CategoryRepository categories;
    private final TagRepository tags;
    private final TopicTagRepository topicTags;
    private final PostVoteRepository votes;
    private final TopicFollowRepository follows;
    private final IdentityApi identity;

    public TopicQueryService(TopicRepository topics, PostRepository posts,
                             CategoryRepository categories, TagRepository tags,
                             TopicTagRepository topicTags, PostVoteRepository votes,
                             TopicFollowRepository follows, IdentityApi identity) {
        this.topics = topics;
        this.posts = posts;
        this.categories = categories;
        this.tags = tags;
        this.topicTags = topicTags;
        this.votes = votes;
        this.follows = follows;
        this.identity = identity;
    }

    @Transactional(readOnly = true)
    public CursorPage<TopicListItem> listByCategory(String slug, String rawCursor, int limit) {
        Category category = categories.findBySlugAndEnabledTrue(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分区不存在"));
        TopicCursor cursor = decode(rawCursor);
        List<Topic> rows = topics.findCategoryPage(category.getId(),
                cursor == null || cursor.pinned(),
                cursor == null ? null : cursor.lastPostAt(),
                cursor == null ? 0L : cursor.id(),
                PageRequest.of(0, clampLimit(limit) + 1));
        return buildPage(rows, clampLimit(limit), Map.of(category.getId(), category));
    }

    @Transactional(readOnly = true)
    public CursorPage<TopicListItem> listLatest(String rawCursor, int limit) {
        TopicCursor cursor = decode(rawCursor);
        List<Topic> rows = topics.findLatestPage(
                cursor == null ? null : cursor.lastPostAt(),
                cursor == null ? 0L : cursor.id(),
                PageRequest.of(0, clampLimit(limit) + 1));
        return buildPage(rows, clampLimit(limit), categoriesOf(rows));
    }

    @Transactional
    public TopicDetailResponse detail(long id, int page, int size) {
        return detail(id, page, size, null);
    }

    @Transactional
    public TopicDetailResponse detail(long id, int page, int size, Long viewerId) {
        Topic topic = topics.findById(id)
                .filter(candidate -> "PUBLIC".equals(candidate.getVisibility())
                        && candidate.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "主题不存在"));
        int pageNumber = Math.max(1, page);
        int pageSize = clampLimit(size);
        Page<Post> postPage = posts.findVisiblePage(id, PageRequest.of(pageNumber - 1, pageSize));

        Category category = categories.findById(topic.getCategoryId()).orElse(null);
        Set<Long> authorIds = new HashSet<>();
        authorIds.add(topic.getAuthorId());
        postPage.getContent().forEach(post -> authorIds.add(post.getAuthorId()));
        Map<Long, UserBrief> authors = authors(authorIds);
        Set<Long> votedPostIds = votedPostIds(viewerId, postPage.getContent());
        boolean following = viewerId != null
                && follows.findById(new TopicFollowId(id, viewerId)).isPresent();

        List<PostView> postViews = postPage.getContent().stream()
                .map(post -> new PostView(post.getId(), post.getFloor(), post.getContent(),
                        authors.getOrDefault(post.getAuthorId(), UserBrief.deleted(post.getAuthorId())),
                        post.getReplyToPostId(), post.getCreatedAt(), post.getUpvoteCount(),
                        votedPostIds.contains(post.getId())))
                .toList();
        List<TagView> tagViews = loadTags(id);
        topics.incrementView(id);

        return new TopicDetailResponse(topic.getId(), topic.getTitle(),
                category != null ? category.getSlug() : "",
                category != null ? category.getName() : "",
                topic.isPinned(), topic.isLocked(), topic.getViewCount() + 1,
                following, topic.getFollowerCount(),
                authors.getOrDefault(topic.getAuthorId(), UserBrief.deleted(topic.getAuthorId())),
                topic.getCreatedAt(), tagViews,
                new PageResponse<>(postViews, postPage.getTotalElements(), pageNumber, pageSize));
    }

    static int clampLimit(int limit) {
        if (limit <= 0) {
            return 20;
        }
        return Math.min(limit, 50);
    }

    private CursorPage<TopicListItem> buildPage(List<Topic> rows, int size,
                                                Map<Long, Category> categoriesById) {
        boolean hasMore = rows.size() > size;
        List<Topic> kept = hasMore ? rows.subList(0, size) : rows;
        List<TopicListItem> items = toItems(kept, categoriesById);
        String next = null;
        if (hasMore && !kept.isEmpty()) {
            Topic last = kept.get(kept.size() - 1);
            next = new TopicCursor(last.isPinned(), last.getLastPostAt(), last.getId()).encode();
        }
        return new CursorPage<>(items, next);
    }

    private List<TopicListItem> toItems(List<Topic> rows, Map<Long, Category> categoriesById) {
        Set<Long> authorIds = rows.stream().map(Topic::getAuthorId).collect(Collectors.toSet());
        Map<Long, UserBrief> authors = authors(authorIds);
        return rows.stream()
                .map(topic -> {
                    Category category = categoriesById.get(topic.getCategoryId());
                    return new TopicListItem(topic.getId(), topic.getTitle(),
                            category != null ? category.getSlug() : "",
                            category != null ? category.getName() : "",
                            topic.isPinned(), topic.isLocked(), topic.getPostCount(),
                            topic.getViewCount(),
                            authors.getOrDefault(topic.getAuthorId(),
                                    UserBrief.deleted(topic.getAuthorId())),
                            topic.getCreatedAt(),
                            topic.getLastPostAt() != null ? topic.getLastPostAt() : topic.getCreatedAt());
                })
                .toList();
    }

    private Map<Long, Category> categoriesOf(List<Topic> rows) {
        Set<Long> ids = rows.stream().map(Topic::getCategoryId).collect(Collectors.toSet());
        return categories.findAllById(ids).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    private Map<Long, UserBrief> authors(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserSummary> summaries = identity.findUsers(ids);
        Map<Long, UserBrief> result = new HashMap<>();
        for (Long id : ids) {
            UserSummary summary = summaries.get(id);
            result.put(id, summary != null ? UserBrief.from(summary) : UserBrief.deleted(id));
        }
        return result;
    }

    private List<TagView> loadTags(long topicId) {
        List<Long> tagIds = topicTags.findTagIdsByTopicId(topicId);
        if (tagIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Tag> byId = tags.findAllById(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, Function.identity()));
        return tagIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(tag -> new TagView(tag.getId(), tag.getName(), tag.getSlug()))
                .toList();
    }

    private Set<Long> votedPostIds(Long viewerId, List<Post> pagePosts) {
        if (viewerId == null || pagePosts.isEmpty()) {
            return Set.of();
        }
        List<Long> postIds = pagePosts.stream().map(Post::getId).toList();
        return Set.copyOf(votes.findVotedPostIds(viewerId, postIds));
    }

    private TopicCursor decode(String rawCursor) {
        if (rawCursor == null || rawCursor.isBlank()) {
            return null;
        }
        return TopicCursor.decode(rawCursor);
    }
}
