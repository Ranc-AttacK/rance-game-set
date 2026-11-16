package com.rance.galcommunity.shared.security;

import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;

public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null
                || rawPassword.length() < 8
                || rawPassword.length() > 72
                || !rawPassword.matches(".*[A-Za-z].*")
                || !rawPassword.matches(".*\\d.*")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "密码需 8-72 位且同时包含字母和数字");
        }
    }
}
