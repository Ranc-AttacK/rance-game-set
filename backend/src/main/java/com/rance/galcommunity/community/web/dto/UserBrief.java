package com.rance.galcommunity.community.web.dto;

import com.rance.galcommunity.identity.api.UserSummary;

public record UserBrief(long id, String nickname, String avatarUrl) {

    public static UserBrief from(UserSummary summary) {
        return new UserBrief(summary.id(), summary.nickname(), summary.avatarUrl());
    }

    public static UserBrief deleted(long id) {
        return new UserBrief(id, "已注销用户", null);
    }
}
