package com.tikkle.investment.service;

import com.tikkle.investment.dto.response.AiRecommendationDto;
import com.tikkle.investment.entity.InvestmentTarget;
import com.tikkle.investment.repository.InvestmentTargetRepository;
import com.tikkle.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvestmentTargetSaver {
    private final InvestmentTargetRepository investmentTargetRepository;
    private final StringRedisTemplate redisTemplate;
    private final JsonMapper objectMapper;

    @Transactional
    public void saveTarget(User user, AiRecommendationDto best) {
        if (best == null) return;

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        // Upsert 로직 (존재하면 Update, 없으면 Insert)
        investmentTargetRepository.findByUserIdAndTargetDate(user.getId(), today)
                .ifPresentOrElse(
                        existing -> {
                            log.info("InvestmentTarget already exists for user {} on {}. Updating target.", user.getId(), today);
                            existing.updateTarget(best.ticker(), best.stockName(), best.reason());
                        },
                        () -> {
                            log.info("Creating new InvestmentTarget for user {} on {}.", user.getId(), today);
                            InvestmentTarget target = InvestmentTarget.builder()
                                    .user(user)
                                    .ticker(best.ticker())
                                    .stockName(best.stockName())
                                    .reason(best.reason())
                                    .targetDate(today)
                                    .build();
                            investmentTargetRepository.save(target);
                        }
                );

        try {
            String redisKey = "user:target:" + user.getId();
            String jsonValue = objectMapper.writeValueAsString(best);
            redisTemplate.opsForValue().set(redisKey, jsonValue, Duration.ofHours(24));
        } catch (Exception e) {
            log.error("Failed to serialize AiRecommendationDto for Redis storage: {}", e.getMessage());
        }
    }
}