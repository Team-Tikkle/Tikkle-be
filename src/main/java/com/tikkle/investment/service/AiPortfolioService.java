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
import org.springframework.core.ParameterizedTypeReference;
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
            Recommend top 5 stocks based on the following investment profile.
            Order them by relevance from 1st to 5th.
            
            - First Return Preference: {first}
            - Second Return Preference: {second}
            - Third Return Preference: {third}
            - Market Preference: {market}
            - Diversification Type: {div}
            - Preferred Themes: {themes}
            - Value Filters: {filters}
            """;

        try {
            return chatClient.prompt()
                    .user(u -> u.text(promptText)
                            .param("first", profile.getFirstReturnPreference())
                            .param("second", profile.getSecondReturnPreference())
                            .param("third", profile.getThirdReturnPreference())
                            .param("market", profile.getMarketPreference())
                            .param("div", profile.getDiversificationType())
                            .param("themes", profile.getPreferredThemes().stream().map(Enum::name).collect(Collectors.joining(",")))
                            .param("filters", profile.getValueFilters().stream().map(Enum::name).collect(Collectors.joining(",")))
                    )
                    .call()
                    .entity(new ParameterizedTypeReference<List<AiRecommendationDto>>() {});
        } catch (Exception e) {
            log.error("AI Recommendation API call failed", e);
            throw new AiRecommendationFailedException();
        }
    }

    private String generateProfileHashKey(InvestmentProfile profile) {
        String first = profile.getFirstReturnPreference().name();
        String second = profile.getSecondReturnPreference().name();
        String third = profile.getThirdReturnPreference().name();
        String market = profile.getMarketPreference().name();
        String div = profile.getDiversificationType().name();

        String themes = profile.getPreferredThemes().stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining("-"));

        String filters = profile.getValueFilters().stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining("-"));

        return String.join(":", first, second, third, market, div, themes, filters);
    }
}