package com.rance.galcommunity.identity.web;

import com.rance.galcommunity.identity.security.OriginGuard;
import com.rance.galcommunity.identity.security.RefreshCookie;
import com.rance.galcommunity.identity.service.AuthService;
import com.rance.galcommunity.identity.web.dto.ForgotPasswordRequest;
import com.rance.galcommunity.identity.web.dto.LoginRequest;
import com.rance.galcommunity.identity.web.dto.LoginResponse;
import com.rance.galcommunity.identity.web.dto.RegisterRequest;
import com.rance.galcommunity.identity.web.dto.ResetPasswordRequest;
import com.rance.galcommunity.identity.web.dto.UserProfileResponse;
import com.rance.galcommunity.identity.web.dto.VerifyEmailRequest;
import com.rance.galcommunity.shared.api.ApiResponse;
import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import com.rance.galcommunity.shared.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookie refreshCookie;
    private final OriginGuard originGuard;

    public AuthController(AuthService authService, RefreshCookie refreshCookie, OriginGuard originGuard) {
        this.authService = authService;
        this.refreshCookie = refreshCookie;
        this.originGuard = originGuard;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request,
                                                     HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.register(request.email(), request.nickname(),
                request.password(), ClientIp.of(httpRequest)));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest,
                                            HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request.email(), request.password(),
                ClientIp.of(httpRequest));
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookie.create(result.refreshToken()).toString());
        return ApiResponse.ok(new LoginResponse(result.access().value(),
                result.access().expiresInSeconds(), result.user()));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(HttpServletRequest httpRequest,
                                              HttpServletResponse response) {
        originGuard.check(httpRequest);
        String raw = refreshCookie.read(httpRequest);
        if (raw == null) {
            throw new BusinessException(ErrorCode.TOKEN_REUSED, "登录已失效，请重新登录");
        }
        AuthService.LoginResult result = authService.refresh(raw);
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookie.create(result.refreshToken()).toString());
        return ApiResponse.ok(new LoginResponse(result.access().value(),
                result.access().expiresInSeconds(), result.user()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest httpRequest,
                                    HttpServletResponse response) {
        originGuard.check(httpRequest);
        authService.logout(refreshCookie.read(httpRequest));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString());
        return ApiResponse.ok();
    }

    @PostMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ApiResponse.ok();
    }

    @PostMapping("/resend-verification")
    public ApiResponse<Void> resendVerification(@AuthenticationPrincipal Jwt jwt) {
        authService.resendVerification(Long.parseLong(jwt.getSubject()));
        return ApiResponse.ok();
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                            HttpServletRequest httpRequest) {
        authService.forgotPassword(request.email(), ClientIp.of(httpRequest));
        return ApiResponse.ok();
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ApiResponse.ok();
    }
}
