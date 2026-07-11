package com.tikkle.user.swagger;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.global.security.CustomUserDetails;
import com.tikkle.user.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "User", description = "유저 API")
public interface UserSwagger {
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "내 정보 조회", description = "로그인한 유저 본인의 정보를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "SUCCESS",
                                      "message": "요청에 성공했습니다.",
                                      "data": {
                                        "id": 1,
                                        "name": "홍길동",
                                        "phoneNumber": "01012345678",
                                        "status": "ACTIVE",
                                        "createdAt": "2024-01-01T00:00:00",
                                        "hasInvestmentProfile": true,
                                        "hasKbankAccount": true,
                                        "hasUpbitKey": true
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "유저를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "USER-001",
                                      "message": "사용자를 찾을 수 없습니다."
                                    }
                                    """)))
    })
    ApiResponse<UserResponse> getMe(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);


    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "회원 탈퇴 (본인)", description = "로그인한 유저 본인을 탈퇴 처리합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "SUCCESS",
                                      "message": "요청에 성공했습니다."
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "유저를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "USER-001",
                                      "message": "사용자를 찾을 수 없습니다."
                                    }
                                    """)))
    })
    ApiResponse<Void> withdrawMe(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

}