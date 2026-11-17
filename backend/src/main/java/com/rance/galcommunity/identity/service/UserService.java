package com.rance.galcommunity.identity.service;

import com.rance.galcommunity.identity.domain.AccountStatus;
import com.rance.galcommunity.identity.domain.UserAccount;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import com.rance.galcommunity.identity.web.dto.PublicUserProfile;
import com.rance.galcommunity.identity.web.dto.UpdateProfileRequest;
import com.rance.galcommunity.identity.web.dto.UserProfileResponse;
import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserAccountRepository users;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserAccountRepository users, RefreshTokenService refreshTokenService,
                       PasswordEncoder passwordEncoder) {
        this.users = users;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PublicUserProfile publicProfile(long id) {
        return PublicUserProfile.from(requireActive(id));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse me(long id) {
        return UserProfileResponse.self(requireActive(id));
    }

    @Transactional
    public UserProfileResponse update(long id, UpdateProfileRequest request) {
        UserAccount user = requireActive(id);
        if (!user.isEmailVerified()) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED, "请先验证邮箱");
        }
        if (request.nickname() != null && !request.nickname().isBlank()) {
            user.setNickname(request.nickname().trim());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().isBlank() ? null : request.avatarUrl().trim());
        }
        if (request.bio() != null) {
            user.setBio(request.bio().isBlank() ? null : request.bio().trim());
        }
        return UserProfileResponse.self(user);
    }

    @Transactional
    public void deleteAccount(long id) {
        UserAccount user = requireActive(id);
        user.setStatus(AccountStatus.DELETED);
        user.setEmail("deleted-" + user.getId() + "@invalid.local");
        user.setNickname("已注销用户");
        user.setAvatarUrl(null);
        user.setBio(null);
        user.setEmailVerified(false);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        refreshTokenService.revokeAllForUser(user.getId());
    }

    private UserAccount requireActive(long id) {
        return users.findById(id)
                .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }
}
