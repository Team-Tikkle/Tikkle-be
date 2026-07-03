package com.tikkle.settings.controller;

import com.tikkle.auth.security.CustomUserDetails;
import com.tikkle.global.response.ApiResponse;
import com.tikkle.settings.dto.request.UpdateLinkedAccountRequest;
import com.tikkle.settings.dto.request.UpdateSpareChangeRulesRequest;
import com.tikkle.settings.dto.response.SettingsResponse;
import com.tikkle.settings.service.SettingsService;
import com.tikkle.settings.swagger.SettingsSwagger;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController implements SettingsSwagger {
    private final SettingsService settingsService;

    @Override
    @GetMapping
    public ApiResponse<SettingsResponse> getSettings(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(settingsService.getSettings(userDetails.getUsername()));
    }


    @Override
    @PatchMapping("/spare-change-rules")
    public ApiResponse<?> updateSpareChangeRules(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                 @RequestBody @Valid UpdateSpareChangeRulesRequest request) {
        settingsService.updateSpareChangeRules(userDetails.getUsername(), request);
        return ApiResponse.successWithNoData();
    }

    @Override
    @PatchMapping("/linked-account")
    public ApiResponse<?> updateLinkedAccount(@AuthenticationPrincipal CustomUserDetails userDetails,
                                              @RequestBody @Valid UpdateLinkedAccountRequest request) {
        settingsService.updateLinkedAccount(userDetails.getUsername(), request);
        return ApiResponse.successWithNoData();
    }
}