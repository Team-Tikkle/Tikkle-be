package com.tikkle.notification.controller;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.global.security.CustomUserDetails;
import com.tikkle.notification.dto.request.DeviceTokenRequest;
import com.tikkle.notification.service.DeviceTokenService;
import com.tikkle.notification.swagger.DeviceTokenSwagger;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FCM 디바이스 토큰 등록/해제 API 엔드포인트를 제공하는 컨트롤러입니다.
 */
@RestController
@RequestMapping("/api/users/me/device-token")
@RequiredArgsConstructor
public class DeviceTokenController implements DeviceTokenSwagger {
    private final DeviceTokenService deviceTokenService;

    @Override
    @PostMapping
    public ApiResponse<Void> registerToken(@AuthenticationPrincipal CustomUserDetails userDetails,
                                           @Valid @RequestBody DeviceTokenRequest request) {
        deviceTokenService.registerToken(userDetails.getUserId(), request.fcmToken());
        return ApiResponse.successWithNoData();
    }

    @Override
    @DeleteMapping
    public ApiResponse<Void> unregisterToken(@AuthenticationPrincipal CustomUserDetails userDetails,
                                             @Valid @RequestBody DeviceTokenRequest request) {
        deviceTokenService.unregisterToken(request.fcmToken());
        return ApiResponse.successWithNoData();
    }
}