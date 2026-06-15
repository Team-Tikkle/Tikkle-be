package com.tikkle.settings.swagger;

import com.tikkle.auth.security.CustomUserDetails;
import com.tikkle.global.response.ApiResponse;
import com.tikkle.settings.dto.request.UpdateExecutionModeRequest;
import com.tikkle.settings.dto.request.UpdateSpareChangeRulesRequest;
import com.tikkle.settings.dto.response.SettingsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Settings", description = "설정 API (투자 룰)")
public interface SettingsSwagger {

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "설정 전체 조회", description = "매매 방식과 7개 카테고리 잔돈 룰을 한 번에 조회합니다. 미설정 카테고리는 NONE으로 반환됩니다.")
    ApiResponse<SettingsResponse> getSettings(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "매매 방식 변경", description = "장중 결제 시 자동/수동 매매 방식(AUTO/MANUAL)을 변경합니다.")
    ApiResponse<?> updateExecutionMode(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
                                       UpdateExecutionModeRequest request);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "카테고리별 잔돈 룰 변경", description = "보낸 카테고리만 부분 갱신합니다. 잔돈 적립을 끄려면 ruleType=NONE을 전송합니다.")
    ApiResponse<?> updateSpareChangeRules(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
                                          UpdateSpareChangeRulesRequest request);
}
