package com.tikkle.payment.entity.enums;

public enum PaymentStatus {
    // [1단계: 필터링 및 분류]
    NOT_INVESTED,       // (기존) 잔돈 0원, 타겟 카드 불일치 등으로 투자 스킵됨
    CLASSIFYING,        // (기존) 1차 캐시 Miss 후, RabbitMQ + AI 워커가 카테고리 분류 중

    // [2단계: 매매 대기] (Market Time Gate 분기점)
    PENDING,            // (기존 의미 구체화) 장외 시간 결제 -> 익일 09:00 Spring Batch 일괄 매수 대기
    WAITING_APPROVAL,   // (신규 추가) 수동 매매 설정 유저 -> 푸시 발송 후 앱 내 '승인' 버튼 클릭 대기

    // [3단계: 매매 실행 및 결과]
    ORDERING,           // (신규 추가) 장중 자동 매매 진행 중 -> KIS API 호출 직전의 임시 상태 (매우 중요!)
    INVESTED,           // (기존) 증권사 API 매수 체결 완료 (최종 성공 상태)

    // [4단계: 예외 및 만료]
    FAILED,             // (신규 추가) 매수 실패 (연동 계좌 잔액 부족, KIS 증권사 서버 에러 등)
    EXPIRED             // (신규 추가) 수동 매매 대기 중 24시간이 지나 스케줄러에 의해 만료된 건
}