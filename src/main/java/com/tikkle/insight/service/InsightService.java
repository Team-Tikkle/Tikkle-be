package com.tikkle.insight.service;

import com.tikkle.insight.dto.response.*;
import com.tikkle.insight.exception.ArticleNotFoundException;
import com.tikkle.insight.repository.BeginnerArticleRepository;
import com.tikkle.insight.repository.InvestmentTermRepository;
import com.tikkle.insight.repository.MarketTopicRepository;
import com.tikkle.insight.repository.RecommendedVideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

/**
 * 인사이트 데이터를 데이터베이스 및 레디스(캐시)에서 조회하여 반환하는 핵심 비즈니스 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InsightService {
    /** 마켓 토픽 목록 캐시 키. 스케줄러가 갱신 후 이 키를 evict 한다. */
    public static final String MARKET_TOPICS_CACHE_KEY = "insight:market-topics";
    private static final Duration MARKET_TOPICS_CACHE_TTL = Duration.ofHours(12);

    private final MarketTopicRepository marketTopicRepository;
    private final InvestmentTermRepository investmentTermRepository;
    private final BeginnerArticleRepository beginnerArticleRepository;
    private final RecommendedVideoRepository recommendedVideoRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final JsonMapper objectMapper;

    /**
     * 최신 마켓 토픽(뉴스)을 캐시 또는 DB에서 조회하여 반환합니다.
     *
     * @return 마켓 토픽 리스트
     */
    public List<MarketTopicResponse> getMarketTopics() {
        List<MarketTopicResponse> cached = readMarketTopicsFromCache();
        if (cached != null) {
            return cached;
        }

        List<MarketTopicResponse> topics = marketTopicRepository.findTop30ByOrderByPublishedAtDesc().stream()
                .map(MarketTopicResponse::from)
                .toList();
        writeMarketTopicsToCache(topics);
        return topics;
    }

    /**
     * 전체 투자 용어집을 정렬하여 반환합니다.
     *
     * @return 투자 용어 리스트
     */
    public List<InvestmentTermResponse> getTerms() {
        return investmentTermRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(InvestmentTermResponse::from)
                .toList();
    }

    /**
     * 초보자 가이드 글 목록(본문 제외)을 정렬하여 반환합니다.
     *
     * @return 초보자 가이드 요약 리스트
     */
    public List<BeginnerArticleSummaryResponse> getArticles() {
        return beginnerArticleRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(BeginnerArticleSummaryResponse::from)
                .toList();
    }

    /**
     * 특정 초보자 가이드 글의 상세 내용(본문 포함)을 조회합니다.
     *
     * @param id 글 ID
     * @return 초보자 가이드 상세 내용
     */
    public BeginnerArticleDetailResponse getArticle(Long id) {
        return beginnerArticleRepository.findById(id)
                .map(BeginnerArticleDetailResponse::from)
                .orElseThrow(ArticleNotFoundException::new);
    }

    /**
     * 추천 영상 목록을 정렬하여 반환합니다.
     *
     * @return 추천 영상 리스트
     */
    public List<RecommendedVideoResponse> getVideos() {
        return recommendedVideoRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(RecommendedVideoResponse::from)
                .toList();
    }

    private List<MarketTopicResponse> readMarketTopicsFromCache() {
        try {
            String json = redisTemplate.opsForValue().get(MARKET_TOPICS_CACHE_KEY);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<List<MarketTopicResponse>>() {});
        } catch (JacksonException | DataAccessException e) {
            // 역직렬화 실패(Jackson) 또는 Redis 장애(DataAccess) 모두 DB 조회로 폴백
            log.warn("[InsightService] 마켓 토픽 캐시 조회 실패 - reason: {}", e.getMessage());
            return null;
        }
    }

    private void writeMarketTopicsToCache(List<MarketTopicResponse> topics) {
        try {
            String json = objectMapper.writeValueAsString(topics);
            redisTemplate.opsForValue().set(MARKET_TOPICS_CACHE_KEY, json, MARKET_TOPICS_CACHE_TTL);
        } catch (JacksonException | DataAccessException e) {
            // 직렬화 실패(Jackson) 또는 Redis 장애(DataAccess) 시 캐싱만 건너뛰고 정상 응답
            log.warn("[InsightService] 마켓 토픽 캐시 저장 실패 - reason: {}", e.getMessage());
        }
    }
}