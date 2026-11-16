package com.rance.galcommunity.shared.security;

import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null) {
            throw invalid();
        }
        int byteLength = rawPassword.getBytes(StandardCharsets.UTF_8).length;
        boolean hasLetter = rawPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
        // BCrypt only considers the first 72 bytes, so enforce the limit in bytes.
        if (rawPassword.length() < 8 || byteLength > 72 || !hasLetter || !hasDigit) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, "密码需 8-72 位且同时包含字母和数字");
    }
}
