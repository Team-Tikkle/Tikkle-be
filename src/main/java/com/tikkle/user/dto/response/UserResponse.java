package com.tikkle.user.dto.response;

import com.tikkle.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "유저 응답")
public record UserResponse(
        @Schema(description = "유저 ID", example = "1") Long id,
        @Schema(description = "이름", example = "홍길동") String name,
        @Schema(description = "전화번호", example = "01012345678") String phoneNumber,
        @Schema(description = "가입일시", example = "2024-01-01T00:00:00") LocalDateTime createdAt,
        @Schema(description = "투자 성향 설정 여부", example = "true") boolean hasInvestmentProfile,
        @Schema(description = "케이뱅크 연동 여부", example = "true") boolean hasKbankAccount,
        @Schema(description = "업비트 연동 여부", example = "true") boolean hasUpbitKey
) {
    public static UserResponse from(User user, boolean hasInvestmentProfile, boolean hasKbankAccount, boolean hasUpbitKey) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getPhoneNumber(),
                user.getCreatedAt(),
                hasInvestmentProfile,
                hasKbankAccount,
                hasUpbitKey
        );
    }
}