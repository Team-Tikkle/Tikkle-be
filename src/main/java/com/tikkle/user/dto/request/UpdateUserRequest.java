package com.tikkle.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "유저 정보 수정 요청")
public record UpdateUserRequest(
        @Schema(description = "이름 (변경하지 않으면 생략)", example = "홍길동")
        @Size(min = 1, max = 50)
        String name,

        @Schema(description = "전화번호 (변경하지 않으면 생략)", example = "010-9876-5432")
        @Size(min = 1, max = 20)
        String phone
) {}