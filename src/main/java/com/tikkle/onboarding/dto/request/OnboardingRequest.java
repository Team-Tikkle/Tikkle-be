package com.tikkle.onboarding.dto.request;

import com.tikkle.investment.entity.enums.*;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.RuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.Set;

/**
 * 온보딩 시 클라이언트로부터 수신하는 통합 Request DTO
 */
@Schema(description = "온보딩 통합 요청 정보")
public record OnboardingRequest(
        // 금융 연동 정보
        @Schema(description = "Upbit 발급 Access Key", example = "LPSaXxxxxx")
        @NotBlank(message = "Upbit Access Key는 필수입니다.")
        String upbitAccessKey,

        @Schema(description = "Upbit 발급 Secret Key", example = "92837498234asdfasdf...")
        @NotBlank(message = "Upbit Secret Key는 필수입니다.")
        String upbitSecretKey,

        @Schema(description = "연동 카드 번호 마지막 4자리", example = "1234")
        @NotBlank(message = "대상 카드 번호 마지막 4자리는 필수입니다.")
        @Size(min = 4, max = 4, message = "카드 번호 마지막 4자리를 입력해주세요.")
        @Pattern(regexp = "^[0-9]{4}$", message = "카드 번호 마지막 4자리는 숫자 4자리여야 합니다.")
        String targetCardLast4,

        // 5대 투자 성향
        @Schema(description = "하락장 방어 심리", example = "HOLD", allowableValues = {"SELL_IMMEDIATELY", "HOLD", "BUY_MORE"})
        @NotNull(message = "하락장 방어 심리를 선택해주세요.")
        RiskTolerance riskTolerance,

        @Schema(description = "트렌드 민감도", example = "PARTIAL_TREND", allowableValues = {"FUNDAMENTAL_ONLY", "PARTIAL_TREND", "FULL_TREND"})
        @NotNull(message = "트렌드 민감도를 선택해주세요.")
        TrendSensitivity trendSensitivity,

        @Schema(description = "관심 산업/테마 리스트 (다중 선택)", example = "[\"LAYER_1\", \"AI\", \"DEFI\"]")
        @NotNull(message = "관심 테마를 지정해주세요.")
        @NotEmpty(message = "관심 테마를 하나 이상 선택해주세요.")
        Set<@NotNull(message = "테마 항목은 null일 수 없습니다.") CryptoTheme> cryptoThemes,

        @Schema(description = "포트폴리오 분산도", example = "BALANCED", allowableValues = {"CONCENTRATED", "BALANCED", "DIVERSIFIED"})
        @NotNull(message = "분산 투자 방식을 선택해주세요.")
        DiversificationType diversificationType,

        @Schema(description = "밈 코인 수용도", example = "SMALL", allowableValues = {"NONE", "SMALL", "ACTIVE"})
        @NotNull(message = "밈 코인 수용도를 선택해주세요.")
        MemeAcceptance memeAcceptance,

        // 7대 카테고리별 잔돈 규칙
        @Schema(description = "7대 결제 카테고리별 잔돈 저축 규칙 리스트", example = "[\n" +
                "  { \"category\": \"CAFE\", \"ruleType\": \"ROUND_UP_10000\" },\n" +
                "  { \"category\": \"MART\", \"ruleType\": \"ROUND_UP_20000\" },\n" +
                "  { \"category\": \"FOOD\", \"ruleType\": \"ROUND_UP_30000\" },\n" +
                "  { \"category\": \"SHOPPING\", \"ruleType\": \"ROUND_UP_40000\" },\n" +
                "  { \"category\": \"TRAFFIC\", \"ruleType\": \"ROUND_UP_50000\" },\n" +
                "  { \"category\": \"CULTURE\", \"ruleType\": \"PERCENT_10\" },\n" +
                "  { \"category\": \"ETC\", \"ruleType\": \"PERCENT_20\" }\n" +
                "]")
        @NotNull(message = "카테고리별 잔돈 규칙은 필수입니다.")
        @Size(min = 7, max = 7, message = "7개의 카테고리 규칙을 모두 설정해야 합니다.")
        @Valid
        List<CategoryRuleDto> categoryRules
) {
    @Schema(description = "카테고리별 잔돈 규칙 정보")
    public record CategoryRuleDto(
            @Schema(description = "결제 카테고리 유형", example = "CAFE", allowableValues = {"CAFE", "MART", "FOOD", "SHOPPING", "TRAFFIC", "CULTURE", "ETC"})
            @NotNull(message = "결제 카테고리는 필수입니다.")
            PaymentCategory category,

            @Schema(description = "잔돈 저축 규칙 유형", example = "ROUND_UP_10000", allowableValues = {"ROUND_UP_10000", "ROUND_UP_20000", "ROUND_UP_30000", "ROUND_UP_40000", "ROUND_UP_50000", "PERCENT_10", "PERCENT_15", "PERCENT_20", "PERCENT_25", "PERCENT_30"})
            @NotNull(message = "잔돈 규칙 유형은 필수입니다.")
            RuleType ruleType
    ) {
    }
}