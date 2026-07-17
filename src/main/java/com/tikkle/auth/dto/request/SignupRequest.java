package com.tikkle.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "회원가입 요청")
public record SignupRequest(
        @Schema(description = "이름", example = "홍길동")
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @Schema(description = "휴대폰 번호 (- 없이 입력)", example = "01012345678")
        @NotBlank(message = "휴대폰 번호는 필수입니다.")
        @Pattern(regexp = "^01[0-9]{8,9}$", message = "올바른 휴대폰 번호 형식이 아닙니다.")
        String phoneNumber,

        @Schema(description = "비밀번호 (영문/숫자/특수문자 포함 8~20자)", example = "password123!")
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,20}$",
                message = "비밀번호는 영문, 숫자, 특수문자를 모두 포함한 8~20자여야 합니다."
        )
        String password,

        @Schema(description = "SMS 인증 후 발급받은 가입용 임시 토큰", example = "uuid-string")
        @NotBlank(message = "인증 토큰은 필수입니다.")
        String signupToken
) {}