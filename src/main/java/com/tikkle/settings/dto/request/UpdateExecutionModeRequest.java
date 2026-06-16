package com.tikkle.settings.dto.request;

import com.tikkle.investment.entity.enums.ExecutionMode;
import jakarta.validation.constraints.NotNull;

public record UpdateExecutionModeRequest(
        @NotNull(message = "매매 방식은 필수입니다.")
        ExecutionMode executionMode
) {
}