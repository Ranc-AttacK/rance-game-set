package com.rance.galcommunity.community.web;

import com.rance.galcommunity.community.service.TopicQueryService;
import com.rance.galcommunity.community.service.TopicWriteService;
import com.rance.galcommunity.community.web.dto.CreatePostRequest;
import com.rance.galcommunity.community.web.dto.CreateTopicRequest;
import com.rance.galcommunity.community.web.dto.PostView;
import com.rance.galcommunity.community.web.dto.TopicDetailResponse;
import com.rance.galcommunity.community.web.dto.TopicListItem;
import com.rance.galcommunity.shared.api.ApiResponse;
import com.rance.galcommunity.shared.api.CursorPage;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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

    public TopicController(TopicQueryService topics, TopicWriteService writes) {
        this.topics = topics;
        this.writes = writes;
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
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(topics.detail(id, page, size));
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

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
