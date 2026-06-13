package com.tikkle.onboarding.dto.request;

import com.tikkle.investment.entity.enums.*;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.RuleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 온보딩 시 클라이언트로부터 수신하는 통합 Request DTO
 */
public record OnboardingRequest(
        // 금융 연동 정보
        @NotBlank(message = "KIS 발급 앱 키는 필수입니다.")
        String kisAppKey,

        @NotBlank(message = "KIS 발급 앱 시크릿 키는 필수입니다.")
        String kisAppSecret,

        @NotBlank(message = "KIS 계좌번호는 필수입니다.")
        String kisAccountNum,

        @NotBlank(message = "대상 카드사는 필수입니다.")
        String targetCardCompany,

        @NotBlank(message = "대상 카드 번호 마지막 4자리는 필수입니다.")
        @Size(min = 4, max = 4, message = "카드 번호 마지막 4자리를 입력해주세요.")
        String targetCardLast4,

        // 9대 투자 성향
        @NotNull(message = "위험 감수 수준을 선택해주세요.")
        RiskTolerance riskTolerance,

        @NotNull(message = "투자 기간을 선택해주세요.")
        InvestmentTerm investmentTerm,

        @NotNull(message = "투자 스타일을 선택해주세요.")
        InvestmentStyle investmentStyle,

        @NotNull(message = "선호 테마를 선택해주세요.")
        PreferredTheme preferredTheme,

        @NotNull(message = "투자 종목 규모 선호를 선택해주세요.")
        StockCapPreference stockCapPreference,

        @NotNull(message = "선호 시장을 선택해주세요.")
        MarketPreference marketPreference,

        @NotNull(message = "ESG 투자 중점을 선택해주세요.")
        EsgFocus esgFocus,

        @NotNull(message = "죄악주 필터 여부를 선택해주세요.")
        SinIndustryFilter sinIndustryFilter,

        @NotNull(message = "수익률 선호를 선택해주세요.")
        ReturnPreference returnPreference,

        @NotNull(message = "분산 투자 유형을 선택해주세요.")
        DiversificationType diversificationType,

        // 투자 공통 설정
        @NotNull(message = "매매 방식을 선택해주세요.")
        ExecutionMode executionMode,

        // 7대 카테고리별 잔돈 규칙
        @NotNull(message = "카테고리별 잔돈 규칙은 필수입니다.")
        @Size(min = 7, max = 7, message = "7개의 카테고리 규칙을 모두 설정해야 합니다.")
        @Valid
        List<CategoryRuleDto> categoryRules
) {
    public record CategoryRuleDto(
            @NotNull(message = "결제 카테고리는 필수입니다.")
            PaymentCategory category,

            @NotNull(message = "잔돈 규칙 유형은 필수입니다.")
            RuleType ruleType
    ) {
    }
}