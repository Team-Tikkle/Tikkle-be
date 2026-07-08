package com.tikkle.settings.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "투자 활성화 여부 업데이트 요청 DTO")
public record UpdateInvestmentStatusRequest(
        @Schema(description = "자동 투자 서비스 활성화 여부", example = "false")
        @NotNull(message = "투자 활성화 여부를 선택해 주세요.")
        Boolean isInvestmentEnabled
) {}