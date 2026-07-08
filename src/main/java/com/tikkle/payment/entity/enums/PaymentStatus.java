package com.tikkle.payment.entity.enums;

public enum PaymentStatus {
    // [필터링 결과]
    NOT_INVESTED,       // 잔돈 0원, 최소 금액 미달 등으로 투자 대상에서 제외됨

    // [매수 대기 및 업비트 입금]
    PENDING_PURCHASE,   // 코인 추천 완료, 사용자의 매수 승인을 대기 중
    PENDING_DEPOSIT,    // 사용자가 승인하여 업비트 입금(2차 인증) 대기 중

    // [매수 결과]
    INVESTED,           // 업비트 매수 체결 완료 (최종 성공)
    FAILED              // 매수 실패 (잔액 부족, 거래소 에러 등)
}