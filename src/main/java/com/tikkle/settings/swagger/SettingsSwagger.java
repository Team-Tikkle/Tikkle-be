package com.tikkle.settings.swagger;

import com.tikkle.global.security.CustomUserDetails;
import com.tikkle.global.response.ApiResponse;
import com.tikkle.settings.dto.request.UpdateInvestmentStatusRequest;
import com.tikkle.settings.dto.request.UpdateSpareChangeRulesRequest;
import com.tikkle.settings.dto.request.UpdateInvestmentProfileRequest;
import com.tikkle.settings.dto.request.UpdateKbankAccountRequest;
import com.tikkle.settings.dto.request.UpdateUpbitKeyRequest;
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
    @Operation(summary = "카테고리별 잔돈 룰 일괄 변경", description = "모든 카테고리(7개)의 잔돈 규칙을 전송하여 전체 갱신합니다.")
    ApiResponse<Void> updateSpareChangeRules(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
                                          UpdateSpareChangeRulesRequest request);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "투자 성향 정보 등록/수정", description = "초기 설정 등록 및 이후 설정 화면에서의 변경을 모두 처리합니다.")
    ApiResponse<Void> updateInvestmentProfile(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
                                           UpdateInvestmentProfileRequest request);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "케이뱅크 계좌/카드 정보 등록/수정", description = "초기 설정 등록 및 이후 변경을 처리합니다.")
    ApiResponse<Void> updateKbankAccount(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
                                      UpdateKbankAccountRequest request);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "업비트 API 키 등록/수정 및 유효성 검증", description = "업비트 키 등록/수정 및 5대 핵심 권한 정밀 검증을 수행합니다.")
    ApiResponse<Void> updateUpbitKey(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
                                  UpdateUpbitKeyRequest request);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "자동 투자 서비스 활성화/비활성화", description = "사용자가 결제 시 투자를 진행할지 여부를 설정합니다.")
    ApiResponse<Void> updateInvestmentStatus(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
                                          UpdateInvestmentStatusRequest request);
}