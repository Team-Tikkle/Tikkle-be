package com.tikkle.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "구글 소셜 로그인 요청")
public record GoogleLoginRequest(
        @Schema(description = "프론트에서 발급받은 구글 액세스 토큰")
        @NotBlank
        String accessToken
) {}