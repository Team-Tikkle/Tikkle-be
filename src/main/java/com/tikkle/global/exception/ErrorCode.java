package com.tikkle.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // 공통
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-001", "서버 내부 오류가 발생했습니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON-002", "잘못된 입력값입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON-003", "지원하지 않는 HTTP 메서드입니다."),
    URL_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-004", "요청하신 URL을 찾을 수 없습니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "COMMON-005", "업로드 가능한 파일 용량을 초과했습니다."),

    // 인증/인가
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH-001", "인증되지 않은 사용자입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-002", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-003", "만료된 토큰입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH-004", "해당 자원에 대한 접근 권한이 없습니다."),
    INVALID_SOCIAL_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-005", "유효하지 않은 소셜 액세스 토큰입니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH-006", "리프레시 토큰이 만료되었습니다. 다시 로그인해주세요."),
    PHONE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "AUTH-007", "이미 가입된 전화번호입니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "AUTH-008", "비밀번호가 일치하지 않습니다."),

    // SMS 본인인증 (SMS)
    SMS_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SMS-001", "SMS 발송에 실패했습니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "SMS-002", "인증번호가 일치하지 않거나 만료되었습니다."),
    EXPIRED_SIGNUP_TOKEN(HttpStatus.BAD_REQUEST, "SMS-003", "휴대폰 인증이 만료되었거나 유효하지 않습니다. 다시 인증해주세요."),

    // 유저 (User)
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-001", "사용자를 찾을 수 없습니다."),
    WITHDRAWN_USER(HttpStatus.FORBIDDEN, "USER-002", "탈퇴한 계정입니다. 탈퇴 후 일정 기간이 지나야 다시 가입할 수 있습니다."),
    LINKED_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-003", "연동된 증권사 계좌 정보를 찾을 수 없습니다."),
    INVALID_TWO_FACTOR_PROVIDER(HttpStatus.BAD_REQUEST, "USER-004", "업비트 2차 인증 수단(카카오톡/네이버/하나은행)이 설정되지 않았거나 올바르지 않습니다."),
    NO_CATEGORY_RULE(HttpStatus.NOT_FOUND, "USER-005", "해당 카테고리에 대한 투자 규칙이 설정되어 있지 않습니다."),

    // 결제 (Payment)
    INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "PAYMENT-001", "유효하지 않은 서명입니다."),
    EXPIRED_TIMESTAMP(HttpStatus.UNAUTHORIZED, "PAYMENT-002", "요청 시간이 만료되었습니다."),
    INVALID_AI_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT-003", "AI 응답 데이터가 유효하지 않습니다."),
    PAYMENT_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT-004", "결제 내역을 찾을 수 없습니다."),
    UNKNOWN_PAYMENT_STATUS(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT-005", "알 수 없는 결제 상태입니다."),
    INVALID_PAYMENT_STATUS(HttpStatus.BAD_REQUEST, "PAYMENT-006", "결제 대기 상태가 아니므로 처리할 수 없습니다."),
    UPBIT_TRADE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT-007", "업비트 매수 주문에 실패했습니다."),
    CARD_MISMATCH(HttpStatus.CONFLICT, "PAYMENT-008", "타겟 카드 결제 건이 아닙니다."),
    DUPLICATE_PAYMENT(HttpStatus.CONFLICT, "PAYMENT-009", "이미 처리된 결제 요청입니다."),
    PAYMENT_FILTER_CONFIGURATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT-010", "요청 바디 캐싱 필터가 적용되지 않았습니다."),
    INVESTMENT_DISABLED(HttpStatus.CONFLICT, "PAYMENT-011", "자동 투자 서비스가 비활성화되어 결제가 무시되었습니다."),

    // 투자 (Investment)
    AI_RECOMMENDATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "INVESTMENT-001", "AI 종목 추천 생성 중 오류가 발생했습니다."),
    MARKET_DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "INVESTMENT-002", "시장 데이터를 찾을 수 없습니다."),
    COIN_NOT_FOUND(HttpStatus.NOT_FOUND, "INVESTMENT-003", "매수 대상 코인을 찾을 수 없습니다."),
    COIN_SYNC_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "INVESTMENT-004", "코인 메타데이터 동기화에 실패했습니다."),
    INVESTMENT_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "INVESTMENT-005", "투자 성향 정보를 찾을 수 없습니다."),

    // 업비트 거래소 연동
    UPBIT_ORDER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "UPBIT-001", "업비트 매수 주문에 실패했습니다."),
    UPBIT_ORDER_INQUIRY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "UPBIT-002", "업비트 체결 내역 조회에 실패했습니다."),
    UPBIT_TOKEN_ISSUE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "UPBIT-003", "업비트 인증 토큰 생성에 실패했습니다."),
    UPBIT_ORDER_EXECUTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "UPBIT-004", "업비트 매수 체결 내역을 확인할 수 없습니다."),
    UPBIT_MARKET_INQUIRY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "UPBIT-005", "업비트 마켓 리스트 조회에 실패했습니다."),
    UPBIT_TICKER_INQUIRY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "UPBIT-006", "업비트 실시간 시세 조회에 실패했습니다."),
    UPBIT_AUTH_PARAM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "UPBIT-007", "업비트 API 호출을 위한 쿼리 파라미터가 유효하지 않습니다."),
    UPBIT_DEPOSIT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "UPBIT-008", "업비트 원화 입금 요청에 실패했습니다."),
    UPBIT_DEPOSIT_INQUIRY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "UPBIT-009", "업비트 입금 내역 조회에 실패했습니다."),
    UPBIT_INVALID_KEY(HttpStatus.UNAUTHORIZED, "UPBIT-010", "유효하지 않거나 권한이 부족한 업비트 API 키입니다."),
    UPBIT_CANDLE_INQUIRY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "UPBIT-011", "업비트 주봉 캔들 차트 조회에 실패했습니다."),

    // 인사이트 (Insight)
    ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "INSIGHT-001", "초보자 글을 찾을 수 없습니다."),
    
    // 시스템/보안 (Security)
    ENCRYPTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SECURITY-001", "데이터 암호화 또는 복호화 중 오류가 발생했습니다."),
    INVALID_ENCRYPTION_KEY(HttpStatus.INTERNAL_SERVER_ERROR, "SECURITY-002", "유효하지 않은 암호화 키 설정입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}