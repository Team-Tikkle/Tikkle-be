package com.tikkle.onboarding.swagger;

import com.tikkle.auth.security.CustomUserDetails;
import com.tikkle.global.response.ApiResponse;
import com.tikkle.onboarding.dto.request.OnboardingRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Onboarding", description = "온보딩 API")
public interface OnboardingSwagger {
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "온보딩 정보 등록", description = "사용자의 투자 성향, 금융 정보, 잔돈 규칙 등 온보딩 정보를 한번에 등록합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "온보딩 정보 등록 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = "{ \"code\": \"SUCCESS\", \"message\": \"요청에 성공했습니다.\", \"data\": null }"
                    ))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 유효성 검증 실패, 온보딩 중복 또는 카테고리 규칙 중복",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "입력값 오류", value = "{ \"code\": \"COMMON-002\", \"message\": \"잘못된 입력값입니다.\" }"),
                            @ExampleObject(name = "온보딩 중복", value = "{ \"code\": \"ONBOARDING-001\", \"message\": \"이미 온보딩을 완료한 사용자입니다.\" }"),
                            @ExampleObject(name = "카테고리 규칙 중복", value = "{ \"code\": \"ONBOARDING-002\", \"message\": \"카테고리별 잔돈 규칙에 중복된 카테고리가 존재합니다.\" }")
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = "{ \"code\": \"USER-001\", \"message\": \"사용자를 찾을 수 없습니다.\" }"
                    )))
    })
    ApiResponse<?> onboard(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody OnboardingRequest request);
}