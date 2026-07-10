package com.tikkle.settings.dto.response;

import com.tikkle.investment.entity.enums.CryptoTheme;
import com.tikkle.investment.entity.enums.DiversificationType;
import com.tikkle.investment.entity.enums.MemeAcceptance;
import com.tikkle.investment.entity.enums.RiskTolerance;
import com.tikkle.investment.entity.enums.TrendSensitivity;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.RuleType;
import com.tikkle.user.entity.enums.TwoFactorProvider;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Set;

@Schema(description = "설정 조회 응답 DTO")
public record SettingsResponse(
        @Schema(description = "등록된 카테고리 잔돈 규칙 목록")
        List<CategoryRule> spareChangeRules,
        
        @Schema(description = "자동 투자 서비스 활성화 여부", example = "true")
        boolean isInvestmentEnabled,

        @Schema(description = "케이뱅크 카드 및 업비트 2차 인증 정보")
        LinkedAccountInfo linkedAccount,

        @Schema(description = "사용자 투자 성향 정보")
        InvestmentProfileInfo investmentProfile
) {
    @Schema(description = "카테고리 잔돈 규칙 아이템")
    public record CategoryRule(
            @Schema(description = "결제 카테고리 유형", example = "CAFE")
            PaymentCategory category, 
            @Schema(description = "잔돈 저축 규칙 유형 (미설정 시 null)", example = "ROUND_UP_10000", nullable = true)
            RuleType ruleType
    ) {}

    @Schema(description = "연동 계좌 정보")
    public record LinkedAccountInfo(
            @Schema(description = "타겟 카드 회사", example = "KBANK")
            String targetCardCompany,
            @Schema(description = "타겟 카드 번호 마지막 4자리", example = "1234")
            String targetCardLast4,
            @Schema(description = "업비트 2차 인증 수단", example = "KAKAOTALK")
            TwoFactorProvider twoFactorProvider
    ) {}

    @Schema(description = "투자 성향 정보")
    public record InvestmentProfileInfo(
            RiskTolerance riskTolerance,
            TrendSensitivity trendSensitivity,
            Set<CryptoTheme> cryptoThemes,
            DiversificationType diversificationType,
            MemeAcceptance memeAcceptance
    ) {}
}