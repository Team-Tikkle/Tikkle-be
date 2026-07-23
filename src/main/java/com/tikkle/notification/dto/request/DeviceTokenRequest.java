package com.tikkle.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "FCM 디바이스 토큰 등록/해제 요청")
public record DeviceTokenRequest(
        @Schema(description = "FCM 디바이스 토큰", example = "dGhpcyBpcyBhIGZ...")
        @NotBlank(message = "FCM 토큰은 필수입니다.")
        String fcmToken
) {}