package com.tikkle.settings.dto.request;

import com.tikkle.investment.entity.enums.ExecutionMode;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "매매 실행 방식 변경 요청")
public record UpdateExecutionModeRequest(
        @Schema(description = "매매 실행 방식 (AUTOMATIC / MANUAL)", example = "AUTOMATIC", allowableValues = {"AUTOMATIC", "MANUAL"})
        @NotNull(message = "매매 방식을 선택해주세요.")
        ExecutionMode executionMode
) {
}