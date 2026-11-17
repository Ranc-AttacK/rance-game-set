package com.rance.galcommunity.identity.web;

import com.rance.galcommunity.identity.security.RefreshCookie;
import com.rance.galcommunity.identity.service.AuthService;
import com.rance.galcommunity.identity.web.dto.LoginRequest;
import com.rance.galcommunity.identity.web.dto.LoginResponse;
import com.rance.galcommunity.identity.web.dto.RegisterRequest;
import com.rance.galcommunity.identity.web.dto.UserProfileResponse;
import com.rance.galcommunity.shared.api.ApiResponse;
import com.rance.galcommunity.shared.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

    public AuthController(AuthService authService, RefreshCookie refreshCookie) {
        this.authService = authService;
        this.refreshCookie = refreshCookie;
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
}
