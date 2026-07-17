package com.tikkle.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "로그인 요청")
public record LoginRequest(
        @Schema(description = "휴대폰 번호 (- 없이 입력)", example = "01012345678")
        @NotBlank(message = "휴대폰 번호는 필수입니다.")
        @Pattern(regexp = "^01[0-9]{8,9}$", message = "올바른 휴대폰 번호 형식이 아닙니다.")
        String phoneNumber,

        // 로그인은 비밀번호를 설정하는 것이 아니라 대조하는 단계이므로 복잡도 검증을 두지 않는다.
        // 검증을 두면 정책 도입 이전에 가입한 계정이 대조 전에 400으로 막혀 로그인 자체가 불가능해진다.
        @Schema(description = "비밀번호", example = "password123!")
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {}