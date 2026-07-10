package com.tikkle.settings.dto.request;

import com.tikkle.investment.entity.enums.RiskTolerance;
import com.tikkle.investment.entity.enums.TrendSensitivity;
import com.tikkle.investment.entity.enums.CryptoTheme;
import com.tikkle.investment.entity.enums.DiversificationType;
import com.tikkle.investment.entity.enums.MemeAcceptance;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "투자 성향 정보 등록 및 수정 요청")
public record UpdateInvestmentProfileRequest(
        @Schema(description = "위험 감수성", example = "HOLD", allowableValues = {"SELL_IMMEDIATELY", "HOLD", "BUY_MORE"})
        @NotNull(message = "위험 감수성 정보는 필수입니다.")
        RiskTolerance riskTolerance,

        @Schema(description = "트렌드 민감도", example = "PARTIAL_TREND", allowableValues = {"FUNDAMENTAL_ONLY", "PARTIAL_TREND", "FULL_TREND"})
        @NotNull(message = "트렌드 민감도 정보는 필수입니다.")
        TrendSensitivity trendSensitivity,

        @Schema(description = "선호 가상자산 테마 목록 (최소 1개, 최대 3개)", example = "[\"LAYER_1\", \"AI\"]", allowableValues = {"LAYER_1", "DEFI", "AI", "WEB3_GAMING", "RWA", "MEME"})
        @NotNull(message = "선호 가상자산 테마 정보는 필수입니다.")
        @Size(min = 1, max = 3, message = "선호 가상자산 테마는 1개 이상, 3개 이하로 선택해야 합니다.")
        List<CryptoTheme> cryptoThemes,

        @Schema(description = "분산 투자 방식", example = "BALANCED", allowableValues = {"CONCENTRATED", "BALANCED", "DIVERSIFIED"})
        @NotNull(message = "분산 투자 방식 정보는 필수입니다.")
        DiversificationType diversificationType,

        @Schema(description = "밈코인 수용 여부", example = "ACTIVE", allowableValues = {"NONE", "SMALL", "ACTIVE"})
        @NotNull(message = "밈코인 수용 여부 정보는 필수입니다.")
        MemeAcceptance memeAcceptance
) {}