package com.tikkle.insight.controller;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.insight.dto.response.BeginnerArticleDetailResponse;
import com.tikkle.insight.dto.response.BeginnerArticleSummaryResponse;
import com.tikkle.insight.dto.response.InvestmentTermResponse;
import com.tikkle.insight.dto.response.MarketTopicResponse;
import com.tikkle.insight.dto.response.RecommendedVideoResponse;
import com.tikkle.insight.service.InsightService;
import com.tikkle.insight.swagger.InsightSwagger;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 인사이트 도메인의 웹 요청 처리를 담당하는 컨트롤러입니다.
 * 마켓 토픽(뉴스), 투자 용어, 초보자 글, 추천 영상 등을 제공합니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/insights")
public class InsightController implements InsightSwagger {
    private final InsightService insightService;

    @Override
    @GetMapping("/market-topics")
    /**
     * 마켓 토픽 목록을 반환합니다.
     *
     * @return 마켓 토픽 응답 DTO 리스트
     */
    public ApiResponse<List<MarketTopicResponse>> getMarketTopics() {
        return ApiResponse.success(insightService.getMarketTopics());
    }

    @Override
    @GetMapping("/terms")
    /**
     * 투자 용어집 전체 목록을 반환합니다.
     *
     * @return 투자 용어 응답 DTO 리스트
     */
    public ApiResponse<List<InvestmentTermResponse>> getTerms() {
        return ApiResponse.success(insightService.getTerms());
    }

    @Override
    @GetMapping("/articles")
    /**
     * 초보자 가이드 글 목록을 반환합니다.
     *
     * @return 초보자 가이드 요약 응답 DTO 리스트
     */
    public ApiResponse<List<BeginnerArticleSummaryResponse>> getArticles() {
        return ApiResponse.success(insightService.getArticles());
    }

    @Override
    @GetMapping("/articles/{id}")
    /**
     * 특정 초보자 가이드 글의 상세 내용(본문 포함)을 반환합니다.
     *
     * @param id 글 ID
     * @return 초보자 가이드 상세 응답 DTO
     */
    public ApiResponse<BeginnerArticleDetailResponse> getArticle(@PathVariable Long id) {
        return ApiResponse.success(insightService.getArticle(id));
    }

    @Override
    @GetMapping("/videos")
    /**
     * 유튜브 추천 영상 목록을 반환합니다.
     *
     * @return 추천 영상 응답 DTO 리스트
     */
    public ApiResponse<List<RecommendedVideoResponse>> getVideos() {
        return ApiResponse.success(insightService.getVideos());
    }
}