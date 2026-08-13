package com.tikkle.notification.service;

import com.google.firebase.messaging.*;
import com.tikkle.notification.entity.DeviceToken;
import com.tikkle.notification.entity.enums.NotificationType;
import com.tikkle.notification.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 결과 알림을 FCM으로 발송하는 서비스입니다.
 * 서버가 단독으로 사용자에게 통보해야 하는 이벤트(체결 완료, 매수 취소·실패, 키 만료, 만료 정리)에만 사용합니다.
 *
 * 3원칙:
 *  1. 트랜잭션 커밋 이후에만 발송한다 (롤백 시 허위 알림 방지).
 *  2. 발송 실패는 절대 상위로 전파하지 않는다 (알림 때문에 결제 파이프라인이 깨지면 안 된다).
 *  3. FCM이 무효로 판정한 토큰(UNREGISTERED/INVALID_ARGUMENT)은 즉시 정리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {
    // Android 클라이언트가 생성한 알림 채널 ID와 반드시 일치해야 한다.
    private static final String ANDROID_CHANNEL_ID = "tikkle_payment_result";

    private final DeviceTokenRepository deviceTokenRepository;
    // tikkle.fcm.enabled=false 이면 FirebaseMessaging 빈이 없으므로 getIfAvailable()은 null을 반환한다.
    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

    /**
     * 지정한 유저의 모든 기기로 결과 알림을 발송합니다.
     * 진행 중인 트랜잭션이 있으면 커밋 이후로 발송을 지연시켜, 롤백된 상태가 알림으로 나가는 것을 막습니다.
     *
     * @param userId  대상 유저 ID
     * @param type    알림 종류 (제목·딥링크 고정)
     * @param body    알림 본문 (발송 시점에 값을 채워 전달)
     * @param eventId 관련 결제 이벤트 ID (data 페이로드에 포함, null 허용)
     */
    public void send(Long userId, NotificationType type, String body, Long eventId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSend(userId, type, body, eventId);
                }
            });
        } else {
            doSend(userId, type, body, eventId);
        }
    }

    private void doSend(Long userId, NotificationType type, String body, Long eventId) {
        try {
            // 발송이 생략되면 사용자에게 아무 흔적도 남지 않으므로, 그 사유는 INFO로 남겨 원인 추적이 가능하게 한다.
            FirebaseMessaging messaging = firebaseMessagingProvider.getIfAvailable();
            if (messaging == null) {
                log.info("[PushNotificationService] FCM 발송 생략 - reason: FCM 비활성화(tikkle.fcm.enabled=false), userId: {}, type: {}", userId, type);
                return;
            }

            List<DeviceToken> deviceTokens = deviceTokenRepository.findByUserId(userId);
            if (deviceTokens.isEmpty()) {
                log.info("[PushNotificationService] FCM 발송 생략 - reason: 등록된 디바이스 토큰 없음(앱의 토큰 등록 미완료), userId: {}, type: {}", userId, type);
                return;
            }

            List<String> tokens = deviceTokens.stream().map(DeviceToken::getFcmToken).toList();

            MulticastMessage.Builder builder = MulticastMessage.builder()
                    .setNotification(Notification.builder()
                            .setTitle(type.getTitle())
                            .setBody(body)
                            .build())
                    .putData("type", type.name())
                    .putData("deepLink", type.getDeepLink())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId(ANDROID_CHANNEL_ID)
                                    .build())
                            .build())
                    .addAllTokens(tokens);

            if (eventId != null) {
                builder.putData("eventId", String.valueOf(eventId));
            }

            BatchResponse response = messaging.sendEachForMulticast(builder.build());
            log.info("[PushNotificationService] FCM 발송 완료 - userId: {}, type: {}, 성공: {}, 실패: {}",
                    userId, type, response.getSuccessCount(), response.getFailureCount());

            if (response.getFailureCount() > 0) {
                cleanUpInvalidTokens(tokens, response);
            }
        } catch (Exception e) {
            // 알림 실패는 결제 파이프라인에 영향을 주면 안 되므로 로깅만 한다.
            log.error("[PushNotificationService] FCM 발송 중 오류 - userId: {}, type: {}", userId, type, e);
        }
    }

    /**
     * FCM이 무효로 판정한 토큰을 정리합니다.
     * UNREGISTERED(앱 삭제/토큰 만료), INVALID_ARGUMENT(잘못된 토큰)만 삭제 대상입니다.
     */
    private void cleanUpInvalidTokens(List<String> tokens, BatchResponse response) {
        List<SendResponse> responses = response.getResponses();
        List<String> deadTokens = new ArrayList<>();

        for (int i = 0; i < responses.size(); i++) {
            SendResponse each = responses.get(i);
            if (each.isSuccessful()) continue;

            MessagingErrorCode errorCode = each.getException() != null
                    ? each.getException().getMessagingErrorCode() : null;
            if (errorCode == MessagingErrorCode.UNREGISTERED
                    || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                deadTokens.add(tokens.get(i));
            }
        }

        for (String deadToken : deadTokens) {
            deviceTokenRepository.deleteByFcmToken(deadToken);
        }
        if (!deadTokens.isEmpty()) {
            log.info("[PushNotificationService] 무효 토큰 정리 완료 - count: {}", deadTokens.size());
        }
    }
}