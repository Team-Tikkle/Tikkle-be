package com.tikkle.settings.swagger;

import com.tikkle.auth.security.CustomUserDetails;
import com.tikkle.global.response.ApiResponse;
import com.tikkle.settings.dto.request.UpdateLinkedAccountRequest;
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
    @Operation(summary = "설정 전체 조회", description = "등록된 카테고리 잔돈 룰을 조회합니다.")
    ApiResponse<SettingsResponse> getSettings(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);


    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "카테고리별 잔돈 룰 변경", description = "보낸 카테고리만 부분 갱신합니다.")
    ApiResponse<?> updateSpareChangeRules(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
                                          UpdateSpareChangeRulesRequest request);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "거래소 계정 및 API 키 수정", description = "Upbit Access Key/Secret Key를 전체 교체합니다. 보안상 기존 값은 조회로 노출하지 않습니다.")
    ApiResponse<?> updateLinkedAccount(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
                                       UpdateLinkedAccountRequest request);
}