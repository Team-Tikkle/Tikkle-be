package com.tikkle.payment.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 클라이언트와의 Server-Sent Events(SSE) 연결을 관리하는 컴포넌트입니다.
 * 타임아웃, 예외 처리 및 Emitter의 생명 주기를 담당합니다.
 */
@Slf4j
@Component
public class SseConnectionManager {
    // paymentEventId -> SseEmitter 매핑
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 새로운 SSE 연결을 생성하고 관리 맵에 등록합니다.
     * 연결 유지 시간은 업비트 2차 인증 유효시간(3분)을 고려해 3분 30초로 설정됩니다.
     *
     * @param eventId 결제 이벤트 ID (연결 식별자)
     * @return 생성된 SseEmitter 객체
     */
    public SseEmitter createEmitter(Long eventId) {
        // 타임아웃 3분 30초 (업비트 2차인증 유효시간 3분 고려하여 넉넉히)
        SseEmitter emitter = new SseEmitter(210000L);
        emitters.put(eventId, emitter);

        emitter.onCompletion(() -> {
            log.info("[SseConnectionManager] SSE 커넥션 완료 - eventId: {}", eventId);
            emitters.remove(eventId);
        });
        emitter.onTimeout(() -> {
            log.warn("[SseConnectionManager] SSE 커넥션 타임아웃 - eventId: {}", eventId);
            emitters.remove(eventId);
        });
        emitter.onError((e) -> {
            log.error("[SseConnectionManager] SSE 커넥션 에러 - eventId: {}", eventId, e);
            emitters.remove(eventId);
        });

        // 클라이언트 최초 연결 시 더미 데이터 발송하여 타임아웃 방지
        send(eventId, "CONNECTED", "SSE 연결 성공");

        return emitter;
    }

    /**
     * 특정 결제 이벤트에 대해 SSE 이벤트를 발송합니다.
     * 발송 실패 시 해당 커넥션은 관리 맵에서 제거됩니다.
     *
     * @param eventId 결제 이벤트 ID
     * @param name 이벤트 이름 (클라이언트가 리스닝할 식별자)
     * @param data 전송할 실제 데이터 객체
     */
    public void send(Long eventId, String name, Object data) {
        SseEmitter emitter = emitters.get(eventId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name(name)
                        .data(data));
            } catch (IOException e) {
                log.error("[SseConnectionManager] SSE 발송 실패 - eventId: {}, name: {}", eventId, name, e);
                emitters.remove(eventId);
            }
        } else {
            log.warn("[SseConnectionManager] 활성화된 SSE 커넥션이 없습니다 - eventId: {}", eventId);
        }
    }

    /**
     * 특정 결제 이벤트에 살아있는 SSE 커넥션이 있는지 확인합니다.
     * FCM 중복 발송을 억제하는 판정에 사용합니다. complete()가 맵에서 제거하므로,
     * 억제 판정은 반드시 send()/complete() 호출 이전에 캡처해야 합니다.
     *
     * @param eventId 결제 이벤트 ID
     * @return 활성 커넥션 존재 여부
     */
    public boolean isConnected(Long eventId) {
        return emitters.containsKey(eventId);
    }

    /**
     * 특정 결제 이벤트의 SSE 연결을 정상적으로 종료(complete)하고 맵에서 제거합니다.
     *
     * @param eventId 결제 이벤트 ID
     */
    public void complete(Long eventId) {
        SseEmitter emitter = emitters.get(eventId);
        if (emitter != null) {
            emitter.complete();
            emitters.remove(eventId);
        }
    }
}