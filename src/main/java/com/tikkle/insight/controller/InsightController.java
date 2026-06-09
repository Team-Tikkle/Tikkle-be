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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/insights")
public class InsightController implements InsightSwagger {

    private final InsightService insightService;

    @Override
    @GetMapping("/market-topics")
    public ApiResponse<List<MarketTopicResponse>> getMarketTopics() {
        return ApiResponse.success(insightService.getMarketTopics());
    }

    @Override
    @GetMapping("/terms")
    public ApiResponse<List<InvestmentTermResponse>> getTerms() {
        return ApiResponse.success(insightService.getTerms());
    }

    @Override
    @GetMapping("/articles")
    public ApiResponse<List<BeginnerArticleSummaryResponse>> getArticles() {
        return ApiResponse.success(insightService.getArticles());
    }

    @Override
    @GetMapping("/articles/{id}")
    public ApiResponse<BeginnerArticleDetailResponse> getArticle(@PathVariable Long id) {
        return ApiResponse.success(insightService.getArticle(id));
    }

    @Override
    @GetMapping("/videos")
    public ApiResponse<List<RecommendedVideoResponse>> getVideos() {
        return ApiResponse.success(insightService.getVideos());
    }
}