package com.tikkle.payment.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSE 연결 관리자가 발송 성공 여부를 어떻게 돌려주는지 확인합니다.
 */
class SseConnectionManagerTest {

    private static final Long EVENT_ID = 1L;

    private final SseConnectionManager sseConnectionManager = new SseConnectionManager();

    @Test
    @DisplayName("연결이 없으면 발송 실패로 돌려준다")
    void returnsFalseWithoutConnection() {
        assertThat(sseConnectionManager.send(EVENT_ID, "PROCESSING", "대기 중")).isFalse();
    }

    @Test
    @DisplayName("열려 있는 연결에는 발송 성공으로 돌려준다")
    void returnsTrueForOpenConnection() {
        sseConnectionManager.createEmitter(EVENT_ID);

        assertThat(sseConnectionManager.send(EVENT_ID, "PROCESSING", "대기 중")).isTrue();
    }

    @Test
    @DisplayName("이미 닫힌 연결이 남아 있으면 예외 없이 발송 실패로 돌려주고 그 연결을 지운다")
    void returnsFalseForClosedConnectionLeftInMap() {
        // 연결이 닫히면 관리자에서 지우는 콜백이 비동기로 돌기 때문에, 닫힌 직후에는 닫힌 연결이 잠시 남는다
        SseEmitter emitter = sseConnectionManager.createEmitter(EVENT_ID);
        emitter.complete();

        assertThat(sseConnectionManager.send(EVENT_ID, "PROCESSING", "대기 중")).isFalse();

        // 지워졌으므로 새로 구독하면 다시 발송할 수 있다
        sseConnectionManager.createEmitter(EVENT_ID);
        assertThat(sseConnectionManager.send(EVENT_ID, "PROCESSING", "대기 중")).isTrue();
    }
}
