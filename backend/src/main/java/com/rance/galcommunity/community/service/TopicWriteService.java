package com.rance.galcommunity.community.service;

import com.rance.galcommunity.community.domain.Category;
import com.rance.galcommunity.community.domain.Post;
import com.rance.galcommunity.community.domain.Tag;
import com.rance.galcommunity.community.domain.Topic;
import com.rance.galcommunity.community.domain.TopicTag;
import com.rance.galcommunity.community.repo.CategoryRepository;
import com.rance.galcommunity.community.repo.PostRepository;
import com.rance.galcommunity.community.repo.TagRepository;
import com.rance.galcommunity.community.repo.TopicRepository;
import com.rance.galcommunity.community.repo.TopicTagRepository;
import com.rance.galcommunity.community.web.dto.CreatePostRequest;
import com.rance.galcommunity.community.web.dto.CreateTopicRequest;
import com.rance.galcommunity.community.web.dto.PostView;
import com.rance.galcommunity.community.web.dto.TopicDetailResponse;
import com.rance.galcommunity.community.web.dto.UserBrief;
import com.rance.galcommunity.identity.api.IdentityApi;
import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import com.rance.galcommunity.shared.ratelimit.RateLimitService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopicWriteService {

    private static final String PUBLIC = "PUBLIC";
    private static final String DISCUSSION = "DISCUSSION";

    private final TopicRepository topics;
    private final PostRepository posts;
    private final CategoryRepository categories;
    private final TagRepository tags;
    private final TopicTagRepository topicTags;
    private final IdentityApi identity;
    private final RateLimitService rateLimit;
    private final TopicQueryService queries;

    public TopicWriteService(TopicRepository topics, PostRepository posts,
                             CategoryRepository categories, TagRepository tags,
                             TopicTagRepository topicTags, IdentityApi identity,
                             RateLimitService rateLimit, TopicQueryService queries) {
        this.topics = topics;
        this.posts = posts;
        this.categories = categories;
        this.tags = tags;
        this.topicTags = topicTags;
        this.identity = identity;
        this.rateLimit = rateLimit;
        this.queries = queries;
    }

    @Transactional
    public TopicDetailResponse createTopic(long userId, CreateTopicRequest request) {
        requireVerified(userId);
        if (!rateLimit.tryAcquire("rl:topic:user:" + userId, 10, Duration.ofHours(1))) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "发帖过于频繁");
        }
        Category category = categories.findById(request.categoryId())
                .filter(Category::isEnabled)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分区不存在"));

        List<String> tagNames = normalizeTags(request.tags());
        if (tagNames.size() < category.getMinTags() || tagNames.size() > category.getMaxTags()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE,
                    "标签数量需在 " + category.getMinTags() + "–" + category.getMaxTags() + " 之间");
        }

        Instant now = Instant.now();
        Topic topic = new Topic();
        topic.setCategoryId(category.getId());
        topic.setAuthorId(userId);
        topic.setKind(DISCUSSION);
        topic.setTitle(request.title().trim());
        topic.setPostCount(1);
        topic.setVisibility(PUBLIC);
        topic.setLastPostAt(now);
        topics.save(topic);

        Post first = new Post();
        first.setTopicId(topic.getId());
        first.setAuthorId(userId);
        first.setContent(request.content().trim());
        first.setFloor(1);
        first.setVisibility(PUBLIC);
        posts.save(first);

        topic.setLastPostId(first.getId());
        categories.incrementAfterTopic(category.getId());
        categories.updateLastPost(category.getId(), first.getId(), now);
        attachTags(topic.getId(), tagNames);
        return queries.detail(topic.getId(), 1, 20);
    }

    @Transactional
    public PostView reply(long userId, long topicId, CreatePostRequest request) {
        requireVerified(userId);
        if (!rateLimit.tryAcquire("rl:post:user:" + userId, 60, Duration.ofHours(1))) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "回帖过于频繁");
        }
        Topic topic = topics.findByIdForUpdate(topicId)
                .filter(candidate -> PUBLIC.equals(candidate.getVisibility())
                        && candidate.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "主题不存在"));
        if (topic.isLocked()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "主题已锁定");
        }
        if (request.replyToPostId() != null) {
            posts.findById(request.replyToPostId())
                    .filter(quoted -> quoted.getTopicId() == topicId
                            && quoted.getDeletedAt() == null)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE, "引用楼层无效"));
        }

        int floor = topic.getPostCount() + 1;
        Post post = new Post();
        post.setTopicId(topicId);
        post.setAuthorId(userId);
        post.setContent(request.content().trim());
        post.setReplyToPostId(request.replyToPostId());
        post.setFloor(floor);
        post.setVisibility(PUBLIC);
        posts.save(post);

        Instant now = Instant.now();
        topic.setPostCount(floor);
        topic.setLastPostId(post.getId());
        topic.setLastPostAt(now);
        categories.incrementPost(topic.getCategoryId());
        categories.updateLastPost(topic.getCategoryId(), post.getId(), now);

        UserBrief author = identity.findUser(userId)
                .map(UserBrief::from)
                .orElseGet(() -> UserBrief.deleted(userId));
        return new PostView(post.getId(), floor, post.getContent(), author,
                post.getReplyToPostId(), post.getCreatedAt());
    }

    private void requireVerified(long userId) {
        if (!identity.isEmailVerified(userId)) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED, "请先验证邮箱");
        }
    }

    private List<String> normalizeTags(List<String> rawTags) {
        if (rawTags == null) {
            return List.of();
        }
        Set<String> unique = rawTags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return List.copyOf(unique);
    }

    private void attachTags(long topicId, List<String> tagNames) {
        if (tagNames.isEmpty()) {
            return;
        }
        Map<String, Tag> existing = tags.findByNameIn(tagNames).stream()
                .collect(Collectors.toMap(Tag::getName, Function.identity()));
        List<Tag> resolved = new ArrayList<>();
        for (String name : tagNames) {
            Tag tag = existing.get(name);
            if (tag == null) {
                tag = new Tag();
                tag.setName(name);
                tag.setSlug(name);
                tags.save(tag);
            }
            resolved.add(tag);
        }
        resolved.forEach(tag -> topicTags.save(new TopicTag(topicId, tag.getId())));
    }
}
