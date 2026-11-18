package com.rance.galcommunity.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rance.galcommunity.community.web.TopicCursor;
import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TopicCursorTest {

    @Test
    void roundTripsMicrosecondPrecision() {
        Instant at = Instant.parse("2026-11-18T06:00:00.123456Z");
        TopicCursor cursor = new TopicCursor(true, at, 42L);

        TopicCursor decoded = TopicCursor.decode(cursor.encode());

        assertThat(decoded.pinned()).isTrue();
        assertThat(decoded.lastPostAt()).isEqualTo(at);
        assertThat(decoded.id()).isEqualTo(42L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-base64!!", "djF8YWJjfDE=", "djF8MXwyfHg=", "djJ8MXwzfDQ="})
    void rejectsMalformed(String raw) {
        assertThatThrownBy(() -> TopicCursor.decode(raw))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }
}
