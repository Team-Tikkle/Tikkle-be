package com.tikkle.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "회원가입 요청")
public record CreateUserRequest(
        @Schema(description = "이름", example = "홍길동")
        @NotBlank @Size(max = 50)
        String name,

        @Schema(description = "이메일", example = "hong@example.com")
        @NotBlank @Email @Size(max = 100)
        String email,

        @Schema(description = "전화번호", example = "010-1234-5678")
        @NotBlank @Size(max = 20)
        String phone
) {}