package com.tikkle.settings.controller;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.global.security.CustomUserDetails;
import com.tikkle.settings.dto.request.UpdateInvestmentProfileRequest;
import com.tikkle.settings.dto.request.UpdateKbankAccountRequest;
import com.tikkle.settings.dto.request.UpdateUpbitKeyRequest;
import com.tikkle.settings.dto.request.UpdateInvestmentStatusRequest;
import com.tikkle.settings.dto.request.UpdateSpareChangeRulesRequest;
import com.tikkle.settings.dto.response.SettingsResponse;
import com.tikkle.settings.service.SettingsService;
import com.tikkle.settings.swagger.SettingsSwagger;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 설정(잔돈 규칙, 투자 성향, 연동 계좌, 자동 투자 등) 관련 API 엔드포인트를 제공하는 컨트롤러입니다.
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController implements SettingsSwagger {
    private final SettingsService settingsService;

    /**
     * 사용자의 설정(잔돈 규칙, 자동 투자 활성화 상태)을 조회합니다.
     *
     * @param userDetails 인증된 사용자 정보
     * @return 설정 정보 응답 객체
     */
    @Override
    @GetMapping
    public ApiResponse<SettingsResponse> getSettings(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(settingsService.getSettings(userDetails.getUserId()));
    }

    /**
     * 카테고리별 잔돈 저축 규칙을 업데이트합니다.
     *
     * @param userDetails 인증된 사용자 정보
     * @param request 변경할 잔돈 규칙 리스트
     * @return 성공 응답
     */
    @Override
    @PatchMapping("/spare-change-rules")
    public ApiResponse<Void> updateSpareChangeRules(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                 @RequestBody @Valid UpdateSpareChangeRulesRequest request) {
        settingsService.updateSpareChangeRules(userDetails.getUserId(), request);
        return ApiResponse.successWithNoData();
    }

    /**
     * 사용자의 투자 성향(위험 감수성, 선호 테마 등)을 업데이트합니다.
     *
     * @param userDetails 인증된 사용자 정보
     * @param request 변경할 투자 성향 정보
     * @return 성공 응답
     */
    @Override
    @PatchMapping("/profile")
    public ApiResponse<Void> updateInvestmentProfile(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                  @RequestBody @Valid UpdateInvestmentProfileRequest request) {
        settingsService.updateInvestmentProfile(userDetails.getUserId(), request);
        return ApiResponse.successWithNoData();
    }

    /**
     * 케이뱅크 연동 카드 정보를 업데이트합니다.
     *
     * @param userDetails 인증된 사용자 정보
     * @param request 변경할 케이뱅크 연동 정보
     * @return 성공 응답
     */
    @Override
    @PatchMapping("/kbank")
    public ApiResponse<Void> updateKbankAccount(@AuthenticationPrincipal CustomUserDetails userDetails,
                                             @RequestBody @Valid UpdateKbankAccountRequest request) {
        settingsService.updateKbankAccount(userDetails.getUserId(), request);
        return ApiResponse.successWithNoData();
    }

    /**
     * 업비트 API 키를 등록 또는 변경하고 유효성을 검증합니다.
     *
     * @param userDetails 인증된 사용자 정보
     * @param request 업비트 API 키 및 2차 인증 정보
     * @return 성공 응답
     */
    @Override
    @PatchMapping("/upbit")
    public ApiResponse<Void> updateUpbitKey(@AuthenticationPrincipal CustomUserDetails userDetails,
                                         @RequestBody @Valid UpdateUpbitKeyRequest request) {
        settingsService.updateUpbitKey(userDetails.getUserId(), request);
        return ApiResponse.successWithNoData();
    }

    /**
     * 자동 투자 서비스의 활성화/비활성화 상태를 업데이트합니다.
     *
     * @param userDetails 인증된 사용자 정보
     * @param request 변경할 자동 투자 상태
     * @return 성공 응답
     */
    @Override
    @PatchMapping("/investment")
    public ApiResponse<Void> updateInvestmentStatus(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                 @RequestBody @Valid UpdateInvestmentStatusRequest request) {
        settingsService.updateInvestmentStatus(userDetails.getUserId(), request);
        return ApiResponse.successWithNoData();
    }
}