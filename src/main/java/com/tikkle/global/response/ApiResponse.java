package com.tikkle.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.tikkle.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * API 공통 응답 형식 클래스
 *
 * @param <T> 응답 데이터의 타입
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder({"code", "message", "data"})
public class ApiResponse<T> {
    private final String code;
    private final String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final T data;

    /**
     * 성공 응답을 생성합니다. (데이터 포함)
     *
     * @param data 응답 데이터
     * @param <T>  데이터 타입
     * @return ApiResponse 객체
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", "요청에 성공했습니다.", data);
    }

    /**
     * 성공 응답을 생성합니다. (커스텀 메시지 및 데이터 포함)
     *
     * @param message 성공 메시지
     * @param data    응답 데이터
     * @param <T>     데이터 타입
     * @return ApiResponse 객체
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>("SUCCESS", message, data);
    }

    /**
     * 성공 응답을 생성합니다. (데이터 없음)
     *
     * @return ApiResponse 객체
     */
    public static ApiResponse<Void> successWithNoData() {
        return new ApiResponse<>("SUCCESS", "요청에 성공했습니다.", null);
    }

    /**
     * 실패 응답을 생성합니다. (에러 코드와 메시지 포함)
     * 추후 ErrorCode Enum 등을 만들면 메서드 파라미터로 받아서 사용하기 좋습니다.
     *
     * @param code    에러 코드
     * @param message 실패 메시지
     * @return ApiResponse 객체
     */
    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    /**
     * 실패 응답을 생성합니다. (ErrorCode Enum 사용)
     *
     * @param errorCode 에러 코드 Enum
     * @return ApiResponse 객체
     */
    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
    }
}