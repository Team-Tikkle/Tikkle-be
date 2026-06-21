package com.tikkle.investment.service;

import com.tikkle.investment.dto.response.AiRecommendationDto;
import com.tikkle.investment.entity.InvestmentProfile;
import com.tikkle.investment.entity.Portfolio;
import com.tikkle.investment.exception.AiRecommendationFailedException;
import com.tikkle.investment.repository.InvestmentProfileRepository;
import com.tikkle.investment.repository.PortfolioRepository;
import com.tikkle.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiPortfolioService {
    private final ChatClient chatClient;
    private final InvestmentProfileRepository investmentProfileRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioScoringEngine scoringEngine;
    private final InvestmentTargetSaver targetSaver;

    public AiPortfolioService(
            @Qualifier("anthropicChatModel") ChatModel anthropicChatModel,
            InvestmentProfileRepository investmentProfileRepository,
            PortfolioRepository portfolioRepository,
            PortfolioScoringEngine scoringEngine,
            InvestmentTargetSaver targetSaver) {
        this.chatClient = ChatClient.builder(anthropicChatModel).build();
        this.investmentProfileRepository = investmentProfileRepository;
        this.portfolioRepository = portfolioRepository;
        this.scoringEngine = scoringEngine;
        this.targetSaver = targetSaver;
    }

    public void generateDailyTargets() {
        log.info("Starting daily portfolio target generation.");
        List<InvestmentProfile> allProfiles = investmentProfileRepository.findAll();

        Map<String, List<InvestmentProfile>> groupedProfiles = allProfiles.stream()
                .collect(Collectors.groupingBy(this::generateProfileHashKey));

        for (Map.Entry<String, List<InvestmentProfile>> entry : groupedProfiles.entrySet()) {
            List<InvestmentProfile> profilesInGroup = entry.getValue();
            InvestmentProfile refProfile = profilesInGroup.get(0);

            try {
                List<AiRecommendationDto> aiRecommendations = fetchAiRecommendations(refProfile);

                List<Long> userIds = profilesInGroup.stream()
                        .map(p -> p.getUser().getId())
                        .toList();
                
                Map<Long, List<Portfolio>> portfoliosByUserId = portfolioRepository.findByUserIdIn(userIds).stream()
                        .collect(Collectors.groupingBy(p -> p.getUser().getId()));

                for (InvestmentProfile profile : profilesInGroup) {
                    try {
                        processUserRecommendation(profile, aiRecommendations, portfoliosByUserId);
                    } catch (Exception innerEx) {
                        log.error("Failed to process recommendation for user {}", profile.getUser().getId(), innerEx);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to fetch recommendations for group: {}", entry.getKey(), e);
            }
        }
        log.info("Finished daily portfolio target generation.");
    }

    private void processUserRecommendation(InvestmentProfile profile, List<AiRecommendationDto> recommendations, Map<Long, List<Portfolio>> portfoliosByUserId) {
        User user = profile.getUser();
        List<Portfolio> portfolios = portfoliosByUserId.getOrDefault(user.getId(), List.of());

        AiRecommendationDto best = scoringEngine.selectBestRecommendation(
                recommendations, portfolios, profile.getDiversificationType());

        targetSaver.saveTarget(user, best);
    }

    private List<AiRecommendationDto> fetchAiRecommendations(InvestmentProfile profile) {
        String promptText = """
            Recommend top 5 cryptocurrencies based on the following investment profile.
            Order them by relevance from 1st to 5th.
            
            - First Return Preference: {first}
            - Second Return Preference: {second}
            - Third Return Preference: {third}
            - Diversification Type: {div}
            - Preferred Themes: {themes}
            - Value Filters: {filters}
            """;

        try {
            // [TODO: OOM 방지] 현재 MVP 단계이므로 findAll()을 사용하지만, 
            // 실 서비스 시에는 Spring Batch 나 Pagination을 활용해 유저를 청크 단위로 처리해야 합니다.
            
            // 외부 AI API 호출 (현재 Step 2 검증을 위해 임시 주석 처리)
            /*
            return chatClient.prompt()
                    .user(u -> u.text(promptText)
                            .param("first", profile.getFirstReturnPreference())
                            .param("second", profile.getSecondReturnPreference())
                            .param("third", profile.getThirdReturnPreference())
                            .param("div", profile.getDiversificationType())
                            .param("themes", profile.getPreferredThemes().stream().map(Enum::name).collect(Collectors.joining(",")))
                            .param("filters", profile.getValueFilters().stream().map(Enum::name).collect(Collectors.joining(",")))
                    )
                    .call()
                    .entity(new ParameterizedTypeReference<List<AiRecommendationDto>>() {});
            */

            // Dummy Data Injection for Step 2 testing
            return List.of(
                    new AiRecommendationDto("KRW-BTC", "비트코인", "최초의 암호화폐이자 디지털 금으로 평가됨"),
                    new AiRecommendationDto("KRW-ETH", "이더리움", "스마트 컨트랙트를 지원하는 최대의 플랫폼 코인"),
                    new AiRecommendationDto("KRW-SOL", "솔라나", "빠른 처리 속도와 낮은 수수료를 강점으로 하는 레이어1"),
                    new AiRecommendationDto("KRW-XRP", "리플", "글로벌 송금 네트워크에 사용되는 디지털 자산"),
                    new AiRecommendationDto("KRW-DOGE", "도지코인", "대표적인 밈 코인으로 강력한 커뮤니티 보유")
            );
        } catch (Exception e) {
            log.error("AI Recommendation API call failed", e);
            throw new AiRecommendationFailedException();
        }
    }

    private String generateProfileHashKey(InvestmentProfile profile) {
        String first = profile.getFirstReturnPreference().name();
        String second = profile.getSecondReturnPreference().name();
        String third = profile.getThirdReturnPreference().name();
        String div = profile.getDiversificationType().name();

        String themes = profile.getPreferredThemes().stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining("-"));

        String filters = profile.getValueFilters().stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining("-"));

        return String.join(":", first, second, third, div, themes, filters);
    }
}