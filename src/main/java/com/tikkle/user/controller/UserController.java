package com.tikkle.user.controller;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.user.swagger.UserSwagger;
import com.tikkle.user.dto.request.CreateUserRequest;
import com.tikkle.user.dto.request.UpdateUserRequest;
import com.tikkle.user.dto.response.UserResponse;
import com.tikkle.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController implements UserSwagger {
    private final UserService userService;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> createUser(@RequestBody @Valid CreateUserRequest request) {
        return ApiResponse.success(userService.createUser(request));
    }

    @Override
    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUser(@PathVariable Long id) {
        return ApiResponse.success(userService.getUser(id));
    }

    @Override
    @PatchMapping("/{id}")
    public ApiResponse<UserResponse> updateUser(@PathVariable Long id,
                                                @RequestBody @Valid UpdateUserRequest request) {
        return ApiResponse.success(userService.updateUser(id, request));
    }

    @Override
    @DeleteMapping("/{id}")
    public ApiResponse<?> withdrawUser(@PathVariable Long id) {
        userService.withdrawUser(id);
        return ApiResponse.successWithNoData();
    }
}