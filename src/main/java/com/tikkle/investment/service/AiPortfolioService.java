package com.tikkle.investment.service;

import com.tikkle.investment.dto.response.AiRecommendationDto;
import com.tikkle.investment.entity.InvestmentProfile;
import com.tikkle.investment.entity.InvestmentTarget;
import com.tikkle.investment.entity.Portfolio;
import com.tikkle.investment.exception.AiRecommendationFailedException;
import com.tikkle.investment.repository.InvestmentProfileRepository;
import com.tikkle.investment.repository.InvestmentTargetRepository;
import com.tikkle.investment.repository.PortfolioRepository;
import com.tikkle.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiPortfolioService {
    private final ChatClient chatClient;
    private final InvestmentProfileRepository investmentProfileRepository;
    private final PortfolioRepository portfolioRepository;
    private final InvestmentTargetRepository investmentTargetRepository;
    private final PortfolioScoringEngine scoringEngine;
    private final StringRedisTemplate redisTemplate;

    public AiPortfolioService(
            @Qualifier("anthropicChatModel") ChatModel anthropicChatModel,
            InvestmentProfileRepository investmentProfileRepository,
            PortfolioRepository portfolioRepository,
            InvestmentTargetRepository investmentTargetRepository,
            PortfolioScoringEngine scoringEngine,
            StringRedisTemplate redisTemplate) {
        this.chatClient = ChatClient.builder(anthropicChatModel).build();
        this.investmentProfileRepository = investmentProfileRepository;
        this.portfolioRepository = portfolioRepository;
        this.investmentTargetRepository = investmentTargetRepository;
        this.scoringEngine = scoringEngine;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
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

                for (InvestmentProfile profile : profilesInGroup) {
                    processUserRecommendation(profile, aiRecommendations);
                }
            } catch (Exception e) {
                log.error("Failed to generate recommendations for group: {}", entry.getKey(), e);
            }
        }
        log.info("Finished daily portfolio target generation.");
    }

    private void processUserRecommendation(InvestmentProfile profile, List<AiRecommendationDto> recommendations) {
        User user = profile.getUser();
        List<Portfolio> portfolios = portfolioRepository.findByUserId(user.getId());

        AiRecommendationDto best = scoringEngine.selectBestRecommendation(
                recommendations, portfolios, profile.getDiversificationType());

        if (best != null) {
            InvestmentTarget target = InvestmentTarget.builder()
                    .user(user)
                    .ticker(best.ticker())
                    .stockName(best.stockName())
                    .reason(best.reason())
                    .targetDate(LocalDate.now())
                    .build();

            investmentTargetRepository.save(target);

            String redisKey = "user:target:" + user.getId();
            redisTemplate.opsForValue().set(redisKey, best.ticker(), Duration.ofHours(24));
        }
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