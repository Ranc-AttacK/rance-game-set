package com.rance.galcommunity.identity.domain;

public enum EmailTemplate {
    VERIFY_EMAIL("验证你的邮箱"),
    RESET_PASSWORD("重置你的密码");

    private final String subject;

    EmailTemplate(String subject) {
        this.subject = subject;
    }

    public String subject() {
        return subject;
    }
}
