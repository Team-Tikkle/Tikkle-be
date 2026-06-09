package com.tikkle.insight.swagger;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.insight.dto.response.BeginnerArticleDetailResponse;
import com.tikkle.insight.dto.response.BeginnerArticleSummaryResponse;
import com.tikkle.insight.dto.response.InvestmentTermResponse;
import com.tikkle.insight.dto.response.MarketTopicResponse;
import com.tikkle.insight.dto.response.RecommendedVideoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Insight", description = "인사이트 API")
public interface InsightSwagger {
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "투데이 마켓 토픽 조회", description = "캐싱된 최신 주식/증시 뉴스 목록을 조회합니다. 카드 터치 시 원문 외부 링크로 이동합니다.")
    ApiResponse<List<MarketTopicResponse>> getMarketTopics();

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "투자 용어집 조회", description = "투자 용어와 뜻 전체 목록을 정렬 순서대로 조회합니다.")
    ApiResponse<List<InvestmentTermResponse>> getTerms();

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "초보자 글 목록 조회", description = "초보자 글 목록(본문 제외)을 조회합니다.")
    ApiResponse<List<BeginnerArticleSummaryResponse>> getArticles();

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "초보자 글 상세 조회", description = "글 본문 전체를 조회합니다. 앱 내부 페이지로 렌더링합니다(외부 링크 X).")
    ApiResponse<BeginnerArticleDetailResponse> getArticle(
            @Parameter(description = "글 ID", example = "1") Long id);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "추천 영상 조회", description = "큐레이션한 추천 영상 목록을 조회합니다. 터치 시 유튜브 외부 링크로 이동합니다.")
    ApiResponse<List<RecommendedVideoResponse>> getVideos();
}