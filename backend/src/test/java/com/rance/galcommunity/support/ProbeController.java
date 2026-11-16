package com.rance.galcommunity.support;

import com.rance.galcommunity.shared.api.ApiResponse;
import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/test/probe")
public class ProbeController {

    public record ProbeRequest(@NotBlank String name) {
    }

    @PostMapping("/validation")
    public ApiResponse<Void> validation(@Valid @RequestBody ProbeRequest request) {
        return ApiResponse.ok();
    }

    @GetMapping("/business")
    public ApiResponse<Void> business() {
        throw new BusinessException(ErrorCode.BUSINESS_RULE, "业务规则不满足");
    }

    @GetMapping("/boom")
    public ApiResponse<Void> boom() {
        throw new IllegalStateException("boom");
    }

    @GetMapping("/typed/{id}")
    public ApiResponse<Long> typed(@PathVariable long id) {
        return ApiResponse.ok(id);
    }

    @GetMapping("/param")
    public ApiResponse<String> param(@RequestParam String q) {
        return ApiResponse.ok(q);
    }

    @GetMapping("/status406")
    public ApiResponse<Void> status406() {
        throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE);
    }

    @GetMapping("/status409")
    public ApiResponse<Void> status409() {
        throw new ResponseStatusException(HttpStatus.CONFLICT);
    }

    @GetMapping("/status503")
    public ApiResponse<Void> status503() {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
