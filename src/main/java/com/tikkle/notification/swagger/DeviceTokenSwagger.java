package com.tikkle.notification.swagger;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.global.security.CustomUserDetails;
import com.tikkle.notification.dto.request.DeviceTokenRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Notification", description = "FCM 디바이스 토큰 API")
public interface DeviceTokenSwagger {

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "디바이스 토큰 등록", description = "FCM 디바이스 토큰을 멱등하게 등록합니다. 이미 다른 계정에 등록된 토큰이면 현재 계정으로 소유권이 이전됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "등록 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "SUCCESS",
                                      "message": "요청에 성공했습니다."
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 입력값 (토큰 누락)",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "COMMON-002",
                                      "message": "FCM 토큰은 필수입니다."
                                    }
                                    """)))
    })
    ApiResponse<Void> registerToken(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
                                    @RequestBody DeviceTokenRequest request);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "디바이스 토큰 해제", description = "로그아웃 시 FCM 디바이스 토큰을 해제합니다. 토큰이 없어도 200을 반환합니다(멱등).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "해제 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "SUCCESS",
                                      "message": "요청에 성공했습니다."
                                    }
                                    """)))
    })
    ApiResponse<Void> unregisterToken(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
                                      @RequestBody DeviceTokenRequest request);
}