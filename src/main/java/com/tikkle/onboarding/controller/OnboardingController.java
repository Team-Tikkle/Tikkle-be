package com.tikkle.onboarding.controller;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.global.security.CustomUserDetails;
import com.tikkle.onboarding.dto.request.OnboardingRequest;
import com.tikkle.onboarding.service.OnboardingService;
import com.tikkle.onboarding.swagger.OnboardingSwagger;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 온보딩 관련 API 요청을 처리하는 컨트롤러 클래스입니다.
 */
@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController implements OnboardingSwagger {
    private final OnboardingService onboardingService;

    /**
     * 사용자의 온보딩 정보(투자 성향, 금융 정보, 잔돈 규칙 등)를 등록합니다.
     *
     * @param userDetails 인증된 사용자 정보
     * @param request     온보딩 요청 DTO
     * @return API 응답 객체
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<?> onboard(@AuthenticationPrincipal CustomUserDetails userDetails,
                                  @Valid @RequestBody OnboardingRequest request) {
        onboardingService.processOnboarding(userDetails.getUserId(), request);
        return ApiResponse.successWithNoData();
    }
}