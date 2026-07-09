package com.tikkle.settings.dto.request;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "거래소 계정 및 API 키 수정 요청")
public record UpdateLinkedAccountRequest(
        @Schema(description = "새로운 Upbit 발급 Access Key", example = "NewAccessKey...")
        @NotBlank(message = "Upbit 발급 Access Key는 필수입니다.")
        String upbitAccessKey,

        @Schema(description = "새로운 Upbit 발급 Secret Key", example = "NewSecretKey...")
        @NotBlank(message = "Upbit 발급 Secret Key는 필수입니다.")
        String upbitSecretKey
) {
    @Override
    public String toString() {
        return "UpdateLinkedAccountRequest{" +
                "upbitAccessKey='***MASKED***'" +
                ", upbitSecretKey='***MASKED***'" +
                '}';
    }
}