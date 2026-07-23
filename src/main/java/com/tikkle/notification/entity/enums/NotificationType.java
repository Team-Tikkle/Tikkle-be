package com.tikkle.notification.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 서버가 FCM으로 단독 발송하는 결과 알림의 종류입니다.
 * 제목(title)과 탭 시 이동할 딥링크(deepLink)는 고정이며, 본문(body)은 발송 시점에 값을 채워 전달합니다.
 * SSE가 이미 끊긴 뒤 발생하거나 사용자가 앱을 떠난 뒤 도착하는 결과에만 사용합니다.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationType {
    TRADE_SUCCESS("매수 체결 완료 🎉", "tikkle://payments"),
    TRADE_TIMEOUT("매수가 취소됐어요", "tikkle://payments"),
    DEPOSIT_FAILED("투자가 취소됐어요", "tikkle://payments"),
    TRADE_FAILED("매수에 실패했어요", "tikkle://payments"),
    UPBIT_INVALID_KEY("업비트 연동이 만료됐어요", "tikkle://settings/api-key"),
    ORDER_EXPIRED("투자 기회가 만료됐어요", "tikkle://payments");

    private final String title;
    private final String deepLink;
}