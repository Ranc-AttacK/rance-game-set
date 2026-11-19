package com.rance.galcommunity.community.service;

import com.rance.galcommunity.community.domain.Post;
import com.rance.galcommunity.community.domain.PostVote;
import com.rance.galcommunity.community.domain.PostVoteId;
import com.rance.galcommunity.community.domain.Topic;
import com.rance.galcommunity.community.domain.TopicFollow;
import com.rance.galcommunity.community.domain.TopicFollowId;
import com.rance.galcommunity.community.repo.PostRepository;
import com.rance.galcommunity.community.repo.PostVoteRepository;
import com.rance.galcommunity.community.repo.TopicFollowRepository;
import com.rance.galcommunity.community.repo.TopicRepository;
import com.rance.galcommunity.community.repo.UserStatRepository;
import com.rance.galcommunity.community.web.dto.FollowResponse;
import com.rance.galcommunity.community.web.dto.VoteResponse;
import com.rance.galcommunity.identity.api.IdentityApi;
import com.rance.galcommunity.notification.api.NotificationApi;
import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InteractionService {

    private static final String PUBLIC = "PUBLIC";

    private final PostRepository posts;
    private final TopicRepository topics;
    private final PostVoteRepository votes;
    private final TopicFollowRepository follows;
    private final UserStatRepository userStats;
    private final IdentityApi identity;
    private final NotificationApi notifications;

    public InteractionService(PostRepository posts, TopicRepository topics,
                              PostVoteRepository votes, TopicFollowRepository follows,
                              UserStatRepository userStats, IdentityApi identity,
                              NotificationApi notifications) {
        this.posts = posts;
        this.topics = topics;
        this.votes = votes;
        this.follows = follows;
        this.userStats = userStats;
        this.identity = identity;
        this.notifications = notifications;
    }

    @Transactional
    public VoteResponse upvote(long userId, long postId) {
        if (!identity.isEmailVerified(userId)) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED, "请先验证邮箱");
        }
        Post post = requireVisiblePost(postId);
        Topic topic = requireVisibleTopic(post.getTopicId());
        if (post.getAuthorId() == userId) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "不能给自己点赞");
        }
        PostVoteId voteId = new PostVoteId(postId, userId);
        if (!votes.existsById(voteId)) {
            votes.save(new PostVote(postId, userId));
            post.setUpvoteCount(post.getUpvoteCount() + 1);
            userStats.adjustReputation(post.getAuthorId(), 1);
            notifications.upvote(post.getAuthorId(), userId, topic.getId(), topic.getTitle(), postId);
        }
        return new VoteResponse(true, post.getUpvoteCount());
    }

    @Transactional
    public VoteResponse removeUpvote(long userId, long postId) {
        Post post = requireVisiblePost(postId);
        PostVoteId voteId = new PostVoteId(postId, userId);
        if (votes.existsById(voteId)) {
            votes.deleteById(voteId);
            post.setUpvoteCount(Math.max(0, post.getUpvoteCount() - 1));
            userStats.adjustReputation(post.getAuthorId(), -1);
        }
        return new VoteResponse(false, post.getUpvoteCount());
    }

    @Transactional
    public FollowResponse follow(long userId, long topicId) {
        Topic topic = requireVisibleTopic(topicId);
        TopicFollowId id = new TopicFollowId(topicId, userId);
        if (!follows.existsById(id)) {
            follows.save(new TopicFollow(topicId, userId, TopicFollow.WATCHING));
            topic.setFollowerCount(topic.getFollowerCount() + 1);
        }
        return new FollowResponse(true, topic.getFollowerCount());
    }

    @Transactional
    public FollowResponse unfollow(long userId, long topicId) {
        Topic topic = requireVisibleTopic(topicId);
        TopicFollowId id = new TopicFollowId(topicId, userId);
        if (follows.existsById(id)) {
            follows.deleteById(id);
            topic.setFollowerCount(Math.max(0, topic.getFollowerCount() - 1));
        }
        return new FollowResponse(false, topic.getFollowerCount());
    }

    private Post requireVisiblePost(long postId) {
        return posts.findById(postId)
                .filter(post -> PUBLIC.equals(post.getVisibility()) && post.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "楼层不存在"));
    }

    private Topic requireVisibleTopic(long topicId) {
        return topics.findById(topicId)
                .filter(topic -> PUBLIC.equals(topic.getVisibility())
                        && topic.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "主题不存在"));
    }
}
