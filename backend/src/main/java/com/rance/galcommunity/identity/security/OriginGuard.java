package com.rance.galcommunity.identity.security;

import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OriginGuard {

    private final Set<String> allowedOrigins;

    public OriginGuard(AppProperties props) {
        this.allowedOrigins = new HashSet<>(props.allowedOrigins());
    }

    public void check(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !allowedOrigins.contains(origin)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "非法请求来源");
        }
    }
}
