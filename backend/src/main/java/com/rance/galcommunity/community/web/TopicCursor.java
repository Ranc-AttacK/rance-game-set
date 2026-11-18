package com.rance.galcommunity.community.web;

import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * Cursor for pinned-first topic lists: (pinned, lastPostAt, id) descending.
 * Microsecond precision keeps MySQL DATETIME(6) values stable in the cursor.
 */
public record TopicCursor(boolean pinned, Instant lastPostAt, long id) {

    private static final String VERSION = "v1";

    public String encode() {
        long micros = ChronoUnit.MICROS.between(Instant.EPOCH, lastPostAt);
        String raw = VERSION + "|" + (pinned ? 1 : 0) + "|" + micros + "|" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static TopicCursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|");
            if (parts.length != 4 || !VERSION.equals(parts[0])
                    || (!"0".equals(parts[1]) && !"1".equals(parts[1]))) {
                throw invalid();
            }
            Instant at = Instant.EPOCH.plus(Long.parseLong(parts[2]), ChronoUnit.MICROS);
            return new TopicCursor("1".equals(parts[1]), at, Long.parseLong(parts[3]));
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_CURSOR, "分页游标无效");
    }
}
