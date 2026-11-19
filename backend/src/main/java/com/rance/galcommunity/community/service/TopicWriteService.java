package com.rance.galcommunity.community.service;

import com.rance.galcommunity.community.domain.Category;
import com.rance.galcommunity.community.domain.Post;
import com.rance.galcommunity.community.domain.PostVoteId;
import com.rance.galcommunity.community.domain.Tag;
import com.rance.galcommunity.community.domain.Topic;
import com.rance.galcommunity.community.domain.TopicFollow;
import com.rance.galcommunity.community.domain.TopicTag;
import com.rance.galcommunity.community.repo.CategoryRepository;
import com.rance.galcommunity.community.repo.PostRepository;
import com.rance.galcommunity.community.repo.PostVoteRepository;
import com.rance.galcommunity.community.repo.TagRepository;
import com.rance.galcommunity.community.repo.TopicFollowRepository;
import com.rance.galcommunity.community.repo.TopicRepository;
import com.rance.galcommunity.community.repo.TopicTagRepository;
import com.rance.galcommunity.community.repo.UserStatRepository;
import com.rance.galcommunity.community.web.dto.CreatePostRequest;
import com.rance.galcommunity.community.web.dto.CreateTopicRequest;
import com.rance.galcommunity.community.web.dto.PostView;
import com.rance.galcommunity.community.web.dto.TopicDetailResponse;
import com.rance.galcommunity.community.web.dto.UserBrief;
import com.rance.galcommunity.identity.api.IdentityApi;
import com.rance.galcommunity.notification.api.NotificationApi;
import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import com.rance.galcommunity.shared.ratelimit.RateLimitService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
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
    private final PostVoteRepository votes;
    private final TopicFollowRepository follows;
    private final UserStatRepository userStats;
    private final IdentityApi identity;
    private final NotificationApi notifications;
    private final RateLimitService rateLimit;
    private final TopicQueryService queries;

    public TopicWriteService(TopicRepository topics, PostRepository posts,
                             CategoryRepository categories, TagRepository tags,
                             TopicTagRepository topicTags, PostVoteRepository votes,
                             TopicFollowRepository follows, UserStatRepository userStats,
                             IdentityApi identity, NotificationApi notifications,
                             RateLimitService rateLimit, TopicQueryService queries) {
        this.topics = topics;
        this.posts = posts;
        this.categories = categories;
        this.tags = tags;
        this.topicTags = topicTags;
        this.votes = votes;
        this.follows = follows;
        this.userStats = userStats;
        this.identity = identity;
        this.notifications = notifications;
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
        userStats.recordTopic(userId, now);
        attachTags(topic.getId(), tagNames);
        return queries.detail(topic.getId(), 1, 20, userId);
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
        Post quoted = null;
        if (request.replyToPostId() != null) {
            quoted = posts.findById(request.replyToPostId())
                    .filter(candidate -> candidate.getTopicId() == topicId
                            && candidate.getDeletedAt() == null)
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
        userStats.recordPost(userId, now);
        notifyReplyRecipients(topic, quoted, userId, post.getId());

        UserBrief author = identity.findUser(userId)
                .map(UserBrief::from)
                .orElseGet(() -> UserBrief.deleted(userId));
        return new PostView(post.getId(), floor, post.getContent(), author,
                post.getReplyToPostId(), post.getCreatedAt(), post.getUpvoteCount(), false);
    }

    @Transactional
    public PostView editPost(long userId, long postId, String content) {
        Post post = posts.findById(postId)
                .filter(candidate -> PUBLIC.equals(candidate.getVisibility())
                        && candidate.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "楼层不存在"));
        requireAuthor(post.getAuthorId(), userId);
        post.setContent(content.trim());
        post.setEditCount(post.getEditCount() + 1);
        UserBrief author = identity.findUser(post.getAuthorId())
                .map(UserBrief::from)
                .orElseGet(() -> UserBrief.deleted(post.getAuthorId()));
        boolean upvoted = votes.existsById(new PostVoteId(post.getId(), userId));
        return new PostView(post.getId(), post.getFloor(), post.getContent(), author,
                post.getReplyToPostId(), post.getCreatedAt(), post.getUpvoteCount(), upvoted);
    }

    @Transactional
    public void deletePost(long userId, long postId) {
        Post post = posts.findById(postId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "楼层不存在"));
        requireAuthor(post.getAuthorId(), userId);
        Topic topic = topics.findByIdForUpdate(post.getTopicId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "主题不存在"));

        post.setDeletedAt(Instant.now());
        recalcTopic(topic);
        recalcCategory(topic.getCategoryId());
        userStats.decrementPosts(post.getAuthorId());
    }

    @Transactional
    public TopicDetailResponse editTopic(long userId, long topicId, String title) {
        Topic topic = requireVisibleTopic(topicId);
        requireAuthor(topic.getAuthorId(), userId);
        topic.setTitle(title.trim());
        return queries.detail(topicId, 1, 20, userId);
    }

    @Transactional
    public void deleteTopic(long userId, long topicId) {
        Topic topic = topics.findByIdForUpdate(topicId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "主题不存在"));
        requireAuthor(topic.getAuthorId(), userId);
        topic.setDeletedAt(Instant.now());
        recalcCategory(topic.getCategoryId());
        userStats.decrementTopics(topic.getAuthorId());
    }

    private void recalcTopic(Topic topic) {
        long visible = posts.countVisible(topic.getId());
        topic.setPostCount((int) visible);
        List<Post> lastPosts = posts.findLastVisible(topic.getId(), PageRequest.of(0, 1));
        if (lastPosts.isEmpty()) {
            topic.setLastPostId(null);
            topic.setLastPostAt(null);
        } else {
            Post last = lastPosts.get(0);
            topic.setLastPostId(last.getId());
            topic.setLastPostAt(last.getCreatedAt());
        }
    }

    private void recalcCategory(long categoryId) {
        Category category = categories.findById(categoryId).orElse(null);
        if (category == null) {
            return;
        }
        category.setTopicCount(topics.countVisibleInCategory(categoryId));
        category.setPostCount(topics.sumVisiblePostCount(categoryId));
        List<Topic> lastTopics = topics.findLastVisibleInCategory(categoryId,
                PageRequest.of(0, 1));
        if (lastTopics.isEmpty()) {
            category.setLastPostId(null);
            category.setLastPostAt(null);
        } else {
            Topic last = lastTopics.get(0);
            category.setLastPostId(last.getLastPostId());
            category.setLastPostAt(last.getLastPostAt());
        }
    }

    private void notifyReplyRecipients(Topic topic, Post quoted, long actorId, long postId) {
        Map<Long, RecipientType> recipients = new LinkedHashMap<>();
        if (topic.getAuthorId() != actorId) {
            recipients.put(topic.getAuthorId(), RecipientType.REPLY);
        }
        if (quoted != null && quoted.getAuthorId() != actorId) {
            recipients.put(quoted.getAuthorId(), RecipientType.QUOTE);
        }
        follows.findByIdTopicIdAndState(topic.getId(), TopicFollow.WATCHING).stream()
                .map(follow -> follow.getId().userId())
                .filter(followerId -> followerId != actorId)
                .forEach(followerId -> recipients.putIfAbsent(followerId, RecipientType.FOLLOW));

        for (Map.Entry<Long, RecipientType> entry : recipients.entrySet()) {
            long recipientId = entry.getKey();
            switch (entry.getValue()) {
                case REPLY -> notifications.reply(recipientId, actorId, topic.getId(),
                        topic.getTitle(), postId);
                case QUOTE -> notifications.quote(recipientId, actorId, topic.getId(),
                        topic.getTitle(), postId);
                case FOLLOW -> notifications.followReply(recipientId, actorId, topic.getId(),
                        topic.getTitle(), postId);
            }
        }
    }

    private enum RecipientType {
        REPLY, QUOTE, FOLLOW
    }

    private Topic requireVisibleTopic(long topicId) {
        return topics.findById(topicId)
                .filter(topic -> PUBLIC.equals(topic.getVisibility())
                        && topic.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "主题不存在"));
    }

    private void requireAuthor(long authorId, long userId) {
        if (authorId != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能操作自己的内容");
        }
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
