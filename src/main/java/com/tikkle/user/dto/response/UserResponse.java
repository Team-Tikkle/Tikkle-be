package com.tikkle.user.dto.response;

import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "유저 응답")
public record UserResponse(
        @Schema(description = "유저 ID", example = "1") Long id,
        @Schema(description = "이름", example = "홍길동") String name,
        @Schema(description = "이메일", example = "hong@example.com") String email,
        @Schema(description = "계정 상태", example = "ACTIVE") UserStatus status,
        @Schema(description = "가입일시", example = "2024-01-01T00:00:00") LocalDateTime createdAt,
        @Schema(description = "투자 성향 설정 여부", example = "true") boolean hasInvestmentProfile,
        @Schema(description = "케이뱅크 연동 여부", example = "true") boolean hasKbankAccount,
        @Schema(description = "업비트 키 연동 여부", example = "true") boolean hasUpbitKey
) {
    public static UserResponse from(User user, boolean hasInvestmentProfile, boolean hasKbankAccount, boolean hasUpbitKey) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getStatus(),
                user.getCreatedAt(),
                hasInvestmentProfile,
                hasKbankAccount,
                hasUpbitKey
        );
    }
}