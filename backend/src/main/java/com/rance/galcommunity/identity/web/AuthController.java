package com.rance.galcommunity.identity.web;

import com.rance.galcommunity.identity.service.AuthService;
import com.rance.galcommunity.identity.web.dto.RegisterRequest;
import com.rance.galcommunity.identity.web.dto.UserProfileResponse;
import com.rance.galcommunity.shared.api.ApiResponse;
import com.rance.galcommunity.shared.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request,
                                                     HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.register(request.email(), request.nickname(),
                request.password(), ClientIp.of(httpRequest)));
    }
}
