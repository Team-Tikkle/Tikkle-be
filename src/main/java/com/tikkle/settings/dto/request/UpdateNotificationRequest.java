package com.tikkle.settings.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationRequest(
        @NotNull(message = "푸시 알림 설정 값은 필수입니다.")
        Boolean pushEnabled
) {
}
