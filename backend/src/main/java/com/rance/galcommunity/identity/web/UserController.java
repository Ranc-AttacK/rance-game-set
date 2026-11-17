package com.rance.galcommunity.identity.web;

import com.rance.galcommunity.identity.security.RefreshCookie;
import com.rance.galcommunity.identity.service.UserService;
import com.rance.galcommunity.identity.web.dto.PublicUserProfile;
import com.rance.galcommunity.identity.web.dto.UpdateProfileRequest;
import com.rance.galcommunity.identity.web.dto.UserProfileResponse;
import com.rance.galcommunity.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final RefreshCookie refreshCookie;

    public UserController(UserService userService, RefreshCookie refreshCookie) {
        this.userService = userService;
        this.refreshCookie = refreshCookie;
    }

    @GetMapping("/{id}")
    public ApiResponse<PublicUserProfile> publicProfile(@PathVariable long id) {
        return ApiResponse.ok(userService.publicProfile(id));
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(userService.me(Long.parseLong(jwt.getSubject())));
    }

    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> update(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.update(Long.parseLong(jwt.getSubject()), request));
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        userService.deleteAccount(Long.parseLong(jwt.getSubject()));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString());
        return ApiResponse.ok();
    }
}
