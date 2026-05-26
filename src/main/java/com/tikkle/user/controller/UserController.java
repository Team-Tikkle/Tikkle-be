package com.tikkle.user.controller;

import com.tikkle.user.dto.request.CreateUserRequest;
import com.tikkle.user.dto.request.UpdateUserRequest;
import com.tikkle.user.dto.response.UserResponse;
import com.tikkle.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "유저 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "회원가입", description = "새로운 유저를 등록합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "SUCCESS",
                                      "message": "요청에 성공했습니다.",
                                      "data": {
                                        "id": 1,
                                        "name": "홍길동",
                                        "email": "hong@example.com",
                                        "phone": "010-1234-5678",
                                        "status": "ACTIVE",
                                        "createdAt": "2024-01-01T00:00:00"
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "잘못된 입력값",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "COMMON-002",
                                      "message": "잘못된 입력값입니다."
                                    }
                                    """))),
            @ApiResponse(responseCode = "409", description = "이미 가입된 이메일",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "USER-002",
                                      "message": "이미 가입된 이메일입니다."
                                    }
                                    """)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public com.tikkle.global.response.ApiResponse<UserResponse> createUser(@RequestBody @Valid CreateUserRequest request) {
        return com.tikkle.global.response.ApiResponse.success(userService.createUser(request));
    }

    @Operation(summary = "유저 조회", description = "ID로 유저 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "SUCCESS",
                                      "message": "요청에 성공했습니다.",
                                      "data": {
                                        "id": 1,
                                        "name": "홍길동",
                                        "email": "hong@example.com",
                                        "phone": "010-1234-5678",
                                        "status": "ACTIVE",
                                        "createdAt": "2024-01-01T00:00:00"
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "유저를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "USER-001",
                                      "message": "사용자를 찾을 수 없습니다."
                                    }
                                    """)))
    })
    @GetMapping("/{id}")
    public com.tikkle.global.response.ApiResponse<UserResponse> getUser(
            @Parameter(description = "유저 ID", example = "1") @PathVariable Long id) {
        return com.tikkle.global.response.ApiResponse.success(userService.getUser(id));
    }

    @Operation(summary = "유저 정보 수정", description = "유저의 이름 또는 전화번호를 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "SUCCESS",
                                      "message": "요청에 성공했습니다.",
                                      "data": {
                                        "id": 1,
                                        "name": "홍길동",
                                        "email": "hong@example.com",
                                        "phone": "010-9876-5432",
                                        "status": "ACTIVE",
                                        "createdAt": "2024-01-01T00:00:00"
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "잘못된 입력값",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "COMMON-002",
                                      "message": "잘못된 입력값입니다."
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "유저를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "USER-001",
                                      "message": "사용자를 찾을 수 없습니다."
                                    }
                                    """)))
    })
    @PatchMapping("/{id}")
    public com.tikkle.global.response.ApiResponse<UserResponse> updateUser(
            @Parameter(description = "유저 ID", example = "1") @PathVariable Long id,
            @RequestBody @Valid UpdateUserRequest request) {
        return com.tikkle.global.response.ApiResponse.success(userService.updateUser(id, request));
    }

    @Operation(summary = "회원 탈퇴", description = "유저를 탈퇴 처리합니다. (소프트 삭제)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "탈퇴 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "SUCCESS",
                                      "message": "요청에 성공했습니다."
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "유저를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "USER-001",
                                      "message": "사용자를 찾을 수 없습니다."
                                    }
                                    """)))
    })
    @DeleteMapping("/{id}")
    public com.tikkle.global.response.ApiResponse<?> withdrawUser(
            @Parameter(description = "유저 ID", example = "1") @PathVariable Long id) {
        userService.withdrawUser(id);
        return com.tikkle.global.response.ApiResponse.successWithNoData();
    }
}
