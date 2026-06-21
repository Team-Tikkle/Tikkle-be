package com.tikkle.payment.entity.enums;

public enum PaymentStatus {
    // [1단계: 필터링 및 분류]
    NOT_INVESTED,       // 잔돈 0원, 타겟 카드 불일치 등으로 투자 스킵됨
    CLASSIFYING,        // 1차 캐시 Miss 후, AI 분류 중

    // [2단계: 매매 대기 (수동 승인)]
    WAITING_APPROVAL,   // 수동 투자 모드에서 사용자의 승인을 대기 중

    // [3단계: 매매 실행 및 결과]
    ORDERING,           // 자동 매매 진행 중 -> 업비트 API 호출 직전의 임시 상태
    INVESTED,           // 업비트 API 매수 체결 완료 (최종 성공 상태)

    // [3단계: 예외]
    FAILED              // 매수 실패 (연동 계좌 잔액 부족, 거래소 서버 에러 등)
}