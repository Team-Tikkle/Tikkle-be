package com.tikkle.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SmsSendRequest {
    @Schema(description = "휴대폰 번호 (- 없이 입력)", example = "01012345678")
    @NotBlank(message = "휴대폰 번호는 필수입니다.")
    private String phoneNumber;
}
