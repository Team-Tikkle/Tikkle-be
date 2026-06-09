package com.tikkle.insight.dto.response;

import com.tikkle.insight.entity.InvestmentTerm;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "투자 용어 응답")
public record InvestmentTermResponse(
        @Schema(description = "용어 ID", example = "1") Long id,
        @Schema(description = "용어명", example = "ETF") String term,
        @Schema(description = "뜻/설명", example = "여러 종목을 묶어 거래소에 상장한 펀드입니다.") String description
) {
    public static InvestmentTermResponse from(InvestmentTerm term) {
        return new InvestmentTermResponse(
                term.getId(),
                term.getTerm(),
                term.getDescription()
        );
    }
}