package com.tikkle.upbit.swagger;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.global.security.CustomUserDetails;
import com.tikkle.upbit.dto.response.UpbitRealtimePortfolioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Upbit Portfolio", description = "업비트 실시간 포트폴리오 연동 API")
public interface UpbitPortfolioSwagger {
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "실시간 업비트 포트폴리오 조회", description = "프론트엔드 웹소켓 연동을 위해 사용자의 실제 업비트 계좌 자산(원금 등)을 조회하여 반환합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않거나 권한이 부족한 업비트 API 키입니다.")
    })
    ApiResponse<UpbitRealtimePortfolioResponse> getRealtimePortfolio(@AuthenticationPrincipal CustomUserDetails userDetails);
}