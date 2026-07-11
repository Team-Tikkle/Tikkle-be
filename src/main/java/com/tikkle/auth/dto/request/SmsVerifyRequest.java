package com.tikkle.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "SMS 검증 요청")
public record SmsVerifyRequest(
        @Schema(description = "휴대폰 번호 (- 없이 입력)", example = "01012345678")
        @NotBlank(message = "휴대폰 번호는 필수입니다.")
        String phoneNumber,

        @Schema(description = "6자리 인증번호", example = "123456")
        @NotBlank(message = "인증번호는 필수입니다.")
        String code
) {}
