package com.tikkle.onboarding.controller;

import com.tikkle.global.security.CustomUserDetails;
import com.tikkle.global.response.ApiResponse;
import com.tikkle.onboarding.dto.request.OnboardingRequest;
import com.tikkle.onboarding.service.OnboardingService;
import com.tikkle.onboarding.swagger.OnboardingSwagger;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController implements OnboardingSwagger {
    private final OnboardingService onboardingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<?> onboard(@AuthenticationPrincipal CustomUserDetails userDetails,
                                  @Valid @RequestBody OnboardingRequest request) {
        onboardingService.processOnboarding(userDetails.getUserId(), request);
        return ApiResponse.successWithNoData();
    }
}