package com.tikkle.settings.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import com.tikkle.user.entity.enums.TwoFactorProvider;

@Schema(description = "업비트 API 키 등록 및 수정 요청")
public record UpdateUpbitKeyRequest(
        @Schema(description = "새로운 Upbit 발급 Access Key", example = "NewAccessKey...")
        @NotBlank(message = "Upbit 발급 Access Key는 필수입니다.")
        String upbitAccessKey,

        @Schema(description = "새로운 Upbit 발급 Secret Key", example = "NewSecretKey...")
        @NotBlank(message = "Upbit 발급 Secret Key는 필수입니다.")
        String upbitSecretKey,

        @Schema(description = "2차 인증 수단", example = "KAKAO")
        @NotNull(message = "2차 인증 수단 정보는 필수입니다.")
        TwoFactorProvider twoFactorProvider
) {
    @Override
    public String toString() {
        return "UpdateUpbitKeyRequest{" +
                "upbitAccessKey='***MASKED***'" +
                ", upbitSecretKey='***MASKED***'" +
                '}';
    }
}