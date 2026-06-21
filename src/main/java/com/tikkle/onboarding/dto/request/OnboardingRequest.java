package com.tikkle.onboarding.dto.request;

import com.tikkle.investment.entity.enums.*;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.RuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.AssertTrue;

import java.util.List;

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

        @Schema(description = "연동할 카드사 명", example = "신한카드")
        @NotBlank(message = "대상 카드사는 필수입니다.")
        @Size(max = 20, message = "대상 카드사는 최대 20자까지 입력 가능합니다.")
        String targetCardCompany,

        @Schema(description = "연동 카드 번호 마지막 4자리", example = "1234")
        @NotBlank(message = "대상 카드 번호 마지막 4자리는 필수입니다.")
        @Size(min = 4, max = 4, message = "카드 번호 마지막 4자리를 입력해주세요.")
        @Pattern(regexp = "^[0-9]{4}$", message = "카드 번호 마지막 4자리는 숫자 4자리여야 합니다.")
        String targetCardLast4,

        // 5대 투자 성향
        @Schema(description = "1순위 수익률 선호 유형", example = "HIGH_RISK_ALTCOIN", allowableValues = {"STABLE_YIELD", "BLUE_CHIP_COIN", "HIGH_RISK_ALTCOIN"})
        @NotNull(message = "1순위 수익률 선호를 선택해주세요.")
        ReturnPreference firstReturnPreference,

        @Schema(description = "2순위 수익률 선호 유형", example = "BLUE_CHIP_COIN", allowableValues = {"STABLE_YIELD", "BLUE_CHIP_COIN", "HIGH_RISK_ALTCOIN"})
        @NotNull(message = "2순위 수익률 선호를 선택해주세요.")
        ReturnPreference secondReturnPreference,

        @Schema(description = "3순위 수익률 선호 유형", example = "STABLE_YIELD", allowableValues = {"STABLE_YIELD", "BLUE_CHIP_COIN", "HIGH_RISK_ALTCOIN"})
        @NotNull(message = "3순위 수익률 선호를 선택해주세요.")
        ReturnPreference thirdReturnPreference,

        @Schema(description = "관심 투자 테마 리스트", example = "[\"DEFI\", \"WEB3\"]")
        @NotNull(message = "선호 테마를 지정해주세요.")
        @NotEmpty(message = "선호 테마를 하나 이상 선택해주세요.")
        List<@NotNull(message = "테마 항목은 null일 수 없습니다.") PreferredTheme> preferredThemes,

        @Schema(description = "가치관 필터링 제외 업종 리스트", example = "[\"PROOF_OF_WORK\", \"SMART_CONTRACT\"]")
        @NotNull(message = "가치관 필터를 지정해주세요.")
        @NotEmpty(message = "가치관 필터를 하나 이상 선택해주세요.")
        List<@NotNull(message = "가치관 필터 항목은 null일 수 없습니다.") ValueFilter> valueFilters,

        @Schema(description = "분산 투자 선호 유형", example = "DIVERSIFIED", allowableValues = {"CONCENTRATED", "DIVERSIFIED"})
        @NotNull(message = "분산 투자 방식을 선택해주세요.")
        DiversificationType diversificationType,

        @Schema(description = "매매 실행 방식", example = "AUTOMATIC", allowableValues = {"AUTOMATIC", "MANUAL"})
        @NotNull(message = "매매 방식을 선택해주세요.")
        ExecutionMode executionMode,

        // 7대 카테고리별 잔돈 규칙
        @Schema(description = "7대 결제 카테고리별 잔돈 저축 규칙 리스트")
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

            @Schema(description = "잔돈 저축 규칙 유형", example = "ROUND_UP_10000", allowableValues = {"ROUND_UP_10000", "ROUND_UP_50000", "PERCENT_5", "PERCENT_10", "PERCENT_20", "PERCENT_30"})
            @NotNull(message = "잔돈 규칙 유형은 필수입니다.")
            RuleType ruleType
    ) {
    }

    @AssertTrue(message = "수익률 선호 순위는 중복될 수 없습니다.")
    public boolean isValidPreferences() {
        if (firstReturnPreference == null || secondReturnPreference == null || thirdReturnPreference == null) {
            return true;
        }
        return firstReturnPreference != secondReturnPreference
                && secondReturnPreference != thirdReturnPreference
                && firstReturnPreference != thirdReturnPreference;
    }
}