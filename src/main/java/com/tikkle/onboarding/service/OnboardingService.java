package com.tikkle.onboarding.service;

import com.tikkle.investment.entity.InvestmentProfile;
import com.tikkle.investment.repository.InvestmentProfileRepository;
import com.tikkle.onboarding.dto.request.OnboardingRequest;
import com.tikkle.onboarding.exception.DuplicateCategoryRuleException;
import com.tikkle.onboarding.exception.OnboardingAlreadyCompletedException;
import com.tikkle.payment.entity.CategorySpareChangeRule;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.repository.CategorySpareChangeRuleRepository;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.TargetCardCompany;
import com.tikkle.user.exception.UserNotFoundException;
import com.tikkle.user.repository.LinkedAccountRepository;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 온보딩 비즈니스 로직을 처리하는 서비스 클래스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {
    private final UserRepository userRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final InvestmentProfileRepository investmentProfileRepository;
    private final CategorySpareChangeRuleRepository categorySpareChangeRuleRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String USER_SETTINGS_CACHE_PREFIX = "user:settings:";

    /**
     * 사용자의 온보딩 절차를 진행합니다.
     * 계좌 연동, 투자 성향 분석, 잔돈 저축 규칙을 저장합니다.
     *
     * @param userId  온보딩을 진행하는 사용자 ID
     * @param request 온보딩 요청 정보
     */
    @Transactional
    public void processOnboarding(Long userId, OnboardingRequest request) {
        log.info("[OnboardingService] 온보딩 처리 시작 - userId: {}", userId);
        try {
            User user = userRepository.findByIdWithLock(userId).orElseThrow(UserNotFoundException::new);

            if (linkedAccountRepository.findByUserId(userId).isPresent() || !categorySpareChangeRuleRepository.findByUserId(userId).isEmpty()) {
                throw new OnboardingAlreadyCompletedException();
            }

            saveLinkedAccount(user, request);
            saveInvestmentProfile(user, request);
            List<CategorySpareChangeRule> rules = saveCategorySpareChangeRules(user, request.categoryRules());

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheUserSettings(userId, request.targetCardLast4(), rules);
                }
            });
        } catch (DataIntegrityViolationException e) {
            log.warn("[OnboardingService] 온보딩 중복 요청 예외 발생 - userId: {}", userId);
            throw new OnboardingAlreadyCompletedException();
        }
    }

    private void saveLinkedAccount(User user, OnboardingRequest dto) {
        LinkedAccount account = LinkedAccount.builder()
                .user(user)
                .upbitAccessKey(dto.upbitAccessKey())
                .upbitSecretKey(dto.upbitSecretKey())
                .targetCardCompany(TargetCardCompany.KBANK.getCompanyName())
                .targetCardLast4(dto.targetCardLast4())
                .twoFactorProvider(dto.twoFactorProvider())
                .build();
        linkedAccountRepository.save(account);
    }

    private void saveInvestmentProfile(User user, OnboardingRequest dto) {
        InvestmentProfile profile = InvestmentProfile.builder()
                .user(user)
                .riskTolerance(dto.riskTolerance())
                .trendSensitivity(dto.trendSensitivity())
                .cryptoThemes(dto.cryptoThemes())
                .diversificationType(dto.diversificationType())
                .memeAcceptance(dto.memeAcceptance())
                .build();
        investmentProfileRepository.save(profile);
    }

    private List<CategorySpareChangeRule> saveCategorySpareChangeRules(User user, List<OnboardingRequest.CategoryRuleDto> ruleDtos) {
        Set<PaymentCategory> distinctCategories = ruleDtos.stream()
                .map(OnboardingRequest.CategoryRuleDto::category)
                .collect(Collectors.toSet());

        if (distinctCategories.size() != ruleDtos.size()) {
            throw new DuplicateCategoryRuleException();
        }

        List<CategorySpareChangeRule> rules = ruleDtos.stream()
                .map(dto -> CategorySpareChangeRule.builder()
                        .user(user)
                        .category(dto.category())
                        .ruleType(dto.ruleType())
                        .build())
                .collect(Collectors.toList());
        return categorySpareChangeRuleRepository.saveAll(rules);
    }

    /**
     * 사용자의 설정 정보를 Redis에 캐싱합니다.
     *
     * @param userId          사용자 ID
     * @param targetCardLast4 타겟 카드 마지막 4자리
     * @param rules           카테고리별 잔돈 저축 규칙 목록
     */
    public void cacheUserSettings(Long userId, String targetCardLast4, List<CategorySpareChangeRule> rules) {
        log.info("[OnboardingService] 사용자 설정 캐싱 시작 - userId: {}", userId);
        String redisKey = USER_SETTINGS_CACHE_PREFIX + userId;

        Map<String, String> cacheData = new HashMap<>();
        cacheData.put("targetCardCompany", TargetCardCompany.KBANK.getCompanyName());
        cacheData.put("targetCardLast4", targetCardLast4);
        cacheData.put("isInvestmentEnabled", "true");

        rules.forEach(rule ->
                cacheData.put(rule.getCategory().name(), rule.getRuleType().name())
        );

        redisTemplate.opsForHash().putAll(redisKey, cacheData);
    }
}