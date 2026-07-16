package com.tikkle.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "비밀번호 재설정 요청")
public record ResetPasswordRequest(
        @Schema(description = "휴대폰 번호 (- 없이 입력)", example = "01012345678")
        @NotBlank(message = "휴대폰 번호는 필수입니다.")
        String phoneNumber,

        @Schema(description = "새 비밀번호", example = "newPassword123!")
        @NotBlank(message = "새 비밀번호는 필수입니다.")
        String newPassword,

        @Schema(description = "SMS 인증 후 발급받은 비밀번호 재설정용 임시 토큰", example = "uuid-string")
        @NotBlank(message = "인증 토큰은 필수입니다.")
        String resetToken
) {}