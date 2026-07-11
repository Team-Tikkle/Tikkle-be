package com.tikkle.user.controller;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.global.security.CustomUserDetails;
import com.tikkle.user.dto.response.UserResponse;
import com.tikkle.user.service.UserService;
import com.tikkle.user.swagger.UserSwagger;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController implements UserSwagger {
    private final UserService userService;

    @Override
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(userService.getMe(userDetails.getUserId()));
    }

    @Override
    @DeleteMapping("/me")
    public ApiResponse<Void> withdrawMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        userService.withdrawMe(userDetails.getUserId());
        return ApiResponse.successWithNoData();
    }
}