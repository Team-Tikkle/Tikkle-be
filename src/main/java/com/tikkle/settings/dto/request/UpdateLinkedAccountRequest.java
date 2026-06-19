package com.tikkle.settings.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateLinkedAccountRequest(
        @NotBlank(message = "KIS 발급 앱 키는 필수입니다.")
        String kisAppKey,

        @NotBlank(message = "KIS 발급 앱 시크릿 키는 필수입니다.")
        String kisAppSecret,

        @NotBlank(message = "KIS 계좌번호는 필수입니다.")
        String kisAccountNum
) {
}