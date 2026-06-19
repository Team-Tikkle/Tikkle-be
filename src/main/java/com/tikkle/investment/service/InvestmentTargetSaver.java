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

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvestmentTargetSaver {
    private final InvestmentTargetRepository investmentTargetRepository;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public void saveTarget(User user, AiRecommendationDto best) {
        if (best == null) return;

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        // 중복 저장 방지 (이미 해당 날짜에 해당 유저의 타겟이 있다면 스킵)
        investmentTargetRepository.findByUserIdAndTargetDate(user.getId(), today)
                .ifPresentOrElse(
                        existing -> log.info("InvestmentTarget already exists for user {} on {}. Skipping.", user.getId(), today),
                        () -> {
                            InvestmentTarget target = InvestmentTarget.builder()
                                    .user(user)
                                    .ticker(best.ticker())
                                    .stockName(best.stockName())
                                    .reason(best.reason())
                                    .targetDate(today)
                                    .build();

                            investmentTargetRepository.save(target);

                            String redisKey = "user:target:" + user.getId();
                            redisTemplate.opsForValue().set(redisKey, best.ticker(), Duration.ofHours(24));
                        }
                );
    }
}