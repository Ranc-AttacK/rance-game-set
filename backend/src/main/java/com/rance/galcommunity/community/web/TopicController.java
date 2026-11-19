package com.rance.galcommunity.community.web;

import com.rance.galcommunity.community.service.InteractionService;
import com.rance.galcommunity.community.service.TopicQueryService;
import com.rance.galcommunity.community.service.TopicWriteService;
import com.rance.galcommunity.community.web.dto.CreatePostRequest;
import com.rance.galcommunity.community.web.dto.CreateTopicRequest;
import com.rance.galcommunity.community.web.dto.FollowResponse;
import com.rance.galcommunity.community.web.dto.PostView;
import com.rance.galcommunity.community.web.dto.TopicDetailResponse;
import com.rance.galcommunity.community.web.dto.TopicListItem;
import com.rance.galcommunity.community.web.dto.UpdatePostRequest;
import com.rance.galcommunity.community.web.dto.UpdateTopicRequest;
import com.rance.galcommunity.community.web.dto.VoteResponse;
import com.rance.galcommunity.shared.api.ApiResponse;
import com.rance.galcommunity.shared.api.CursorPage;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TopicController {

    private final TopicQueryService topics;
    private final TopicWriteService writes;
    private final InteractionService interactions;

    public TopicController(TopicQueryService topics, TopicWriteService writes,
                           InteractionService interactions) {
        this.topics = topics;
        this.writes = writes;
        this.interactions = interactions;
    }

    @GetMapping("/categories/{slug}/topics")
    public ApiResponse<CursorPage<TopicListItem>> byCategory(
            @PathVariable String slug,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(topics.listByCategory(slug, cursor, limit));
    }

    @GetMapping("/topics")
    public ApiResponse<CursorPage<TopicListItem>> latest(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(topics.listLatest(cursor, limit));
    }

    @GetMapping("/topics/{id}")
    public ApiResponse<TopicDetailResponse> detail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(topics.detail(id, page, size, jwt == null ? null : userId(jwt)));
    }

    @PostMapping("/topics")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TopicDetailResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody CreateTopicRequest request) {
        return ApiResponse.ok(writes.createTopic(userId(jwt), request));
    }

    @PostMapping("/topics/{id}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostView> reply(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable long id,
                                       @Valid @RequestBody CreatePostRequest request) {
        return ApiResponse.ok(writes.reply(userId(jwt), id, request));
    }

    @PatchMapping("/posts/{id}")
    public ApiResponse<PostView> editPost(@AuthenticationPrincipal Jwt jwt,
                                          @PathVariable long id,
                                          @Valid @RequestBody UpdatePostRequest request) {
        return ApiResponse.ok(writes.editPost(userId(jwt), id, request.content()));
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> deletePost(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable long id) {
        writes.deletePost(userId(jwt), id);
        return ApiResponse.ok();
    }

    @PatchMapping("/topics/{id}")
    public ApiResponse<TopicDetailResponse> editTopic(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @Valid @RequestBody UpdateTopicRequest request) {
        return ApiResponse.ok(writes.editTopic(userId(jwt), id, request.title()));
    }

    @DeleteMapping("/topics/{id}")
    public ApiResponse<Void> deleteTopic(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable long id) {
        writes.deleteTopic(userId(jwt), id);
        return ApiResponse.ok();
    }

    @PostMapping("/posts/{id}/upvote")
    public ApiResponse<VoteResponse> upvote(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable long id) {
        return ApiResponse.ok(interactions.upvote(userId(jwt), id));
    }

    @DeleteMapping("/posts/{id}/upvote")
    public ApiResponse<VoteResponse> removeUpvote(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable long id) {
        return ApiResponse.ok(interactions.removeUpvote(userId(jwt), id));
    }

    @PostMapping("/topics/{id}/follow")
    public ApiResponse<FollowResponse> follow(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable long id) {
        return ApiResponse.ok(interactions.follow(userId(jwt), id));
    }

    @DeleteMapping("/topics/{id}/follow")
    public ApiResponse<FollowResponse> unfollow(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable long id) {
        return ApiResponse.ok(interactions.unfollow(userId(jwt), id));
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
