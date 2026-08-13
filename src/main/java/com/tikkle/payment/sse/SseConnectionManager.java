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

        // 콜백은 비동기로 실행되므로, 재연결로 이미 교체된 뒤에 늦게 도착한 콜백이
        // 새 커넥션을 지우지 않도록 자기 자신이 맵에 남아있을 때만 제거한다.
        emitter.onCompletion(() -> {
            log.info("[SseConnectionManager] SSE 커넥션 완료 - eventId: {}", eventId);
            emitters.remove(eventId, emitter);
        });
        emitter.onTimeout(() -> {
            log.warn("[SseConnectionManager] SSE 커넥션 타임아웃 - eventId: {}", eventId);
            emitters.remove(eventId, emitter);
        });
        emitter.onError((e) -> {
            log.error("[SseConnectionManager] SSE 커넥션 에러 - eventId: {}", eventId, e);
            emitters.remove(eventId, emitter);
        });

        // 앱 이탈 후 재구독하는 경우를 위해 원자적으로 교체한다.
        // remove 후 put으로 나누면 그 사이에 도착한 발송이 "커넥션 없음"으로 유실된다.
        SseEmitter previous = emitters.put(eventId, emitter);
        if (previous != null) {
            log.info("[SseConnectionManager] 이전 SSE 커넥션 정리 후 재연결 - eventId: {}", eventId);
            previous.complete();
        }

        // 클라이언트 최초 연결 시 더미 데이터 발송하여 타임아웃 방지
        send(eventId, "CONNECTED", "SSE 연결 성공");

        return emitter;
    }

    /**
     * 특정 결제 이벤트에 대해 SSE 이벤트를 발송하고, 실제 발송 여부를 반환합니다.
     * 발송 실패 시 해당 커넥션은 관리 맵에서 제거됩니다.
     *
     * <p>호출부는 이 반환값으로 FCM 대체 발송 여부를 판단해야 합니다.
     * 커넥션 존재 여부(맵에 남아있는지)만으로 판단하면, 클라이언트가 이미 떠났지만
     * 서버가 아직 그 사실을 모르는 구간에서 발송이 실패했는데도 FCM이 억제되어 결과가 유실됩니다.
     * 서버는 다음 쓰기를 시도할 때 비로소 끊김을 알게 되므로, 그 쓰기가 곧 최종 결과인 경우가 많습니다.
     *
     * @param eventId 결제 이벤트 ID
     * @param name 이벤트 이름 (클라이언트가 리스닝할 식별자)
     * @param data 전송할 실제 데이터 객체
     * @return 발송에 성공했으면 true, 커넥션이 없거나 발송이 실패했으면 false
     */
    public boolean send(Long eventId, String name, Object data) {
        SseEmitter emitter = emitters.get(eventId);
        if (emitter == null) {
            log.warn("[SseConnectionManager] 활성화된 SSE 커넥션이 없습니다 - eventId: {}", eventId);
            return false;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name(name)
                    .data(data));
            return true;
        } catch (IOException e) {
            log.error("[SseConnectionManager] SSE 발송 실패 - eventId: {}, name: {}", eventId, name, e);
            emitters.remove(eventId, emitter);
            return false;
        }
    }

    /**
     * 특정 결제 이벤트의 SSE 연결을 정상적으로 종료(complete)하고 맵에서 제거합니다.
     *
     * @param eventId 결제 이벤트 ID
     */
    public void complete(Long eventId) {
        SseEmitter emitter = emitters.remove(eventId);
        if (emitter != null) {
            emitter.complete();
        }
    }
}