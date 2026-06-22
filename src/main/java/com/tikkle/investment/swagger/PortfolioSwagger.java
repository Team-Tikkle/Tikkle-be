package com.tikkle.investment.swagger;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.investment.dto.response.PortfolioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Portfolio", description = "홈(포트폴리오) 화면 API")
public interface PortfolioSwagger {
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "홈 포트폴리오 스냅샷 조회", description = "사용자의 코인 보유 자산과 업비트 실시간 시세를 결합한 스냅샷을 반환합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공")
    })
    ApiResponse<PortfolioResponse> getPortfolio(@AuthenticationPrincipal com.tikkle.auth.security.CustomUserDetails userDetails);
}