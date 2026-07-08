package com.tikkle.user.controller;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.global.security.CustomUserDetails;
import com.tikkle.user.dto.request.UpdateUserRequest;
import com.tikkle.user.dto.response.UserResponse;
import com.tikkle.user.service.UserService;
import com.tikkle.user.swagger.UserSwagger;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateMe(@AuthenticationPrincipal CustomUserDetails userDetails,
                                              @RequestBody @Valid UpdateUserRequest request) {
        return ApiResponse.success(userService.updateMe(userDetails.getUserId(), request));
    }

    @Override
    @DeleteMapping("/me")
    public ApiResponse<?> withdrawMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        userService.withdrawMe(userDetails.getUserId());
        return ApiResponse.successWithNoData();
    }
}