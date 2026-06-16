package com.tikkle.onboarding.service;

import com.tikkle.investment.entity.InvestmentProfile;
import com.tikkle.investment.entity.InvestmentSettings;
import com.tikkle.investment.repository.InvestmentProfileRepository;
import com.tikkle.investment.repository.InvestmentSettingsRepository;
import com.tikkle.onboarding.dto.request.OnboardingRequest;
import com.tikkle.onboarding.exception.DuplicateCategoryRuleException;
import com.tikkle.onboarding.exception.OnboardingAlreadyCompletedException;
import com.tikkle.payment.entity.CategorySpareChangeRule;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.repository.CategorySpareChangeRuleRepository;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.entity.User;
import com.tikkle.user.exception.UserNotFoundException;
import com.tikkle.user.repository.LinkedAccountRepository;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class OnboardingService {
    private final UserRepository userRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final InvestmentProfileRepository investmentProfileRepository;
    private final InvestmentSettingsRepository investmentSettingsRepository;
    private final CategorySpareChangeRuleRepository categorySpareChangeRuleRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String USER_SETTINGS_CACHE_PREFIX = "user:settings:";

    @Transactional
    public void processOnboarding(Long userId, OnboardingRequest request) {
        try {
            User user = userRepository.findByIdWithLock(userId).orElseThrow(UserNotFoundException::new);

            if (linkedAccountRepository.findByUserId(userId).isPresent() || !categorySpareChangeRuleRepository.findByUserId(userId).isEmpty()) {
                throw new OnboardingAlreadyCompletedException();
            }

            saveLinkedAccount(user, request);
            saveInvestmentProfile(user, request);
            saveInvestmentSettings(user, request);
            List<CategorySpareChangeRule> rules = saveCategorySpareChangeRules(user, request.categoryRules());

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    warmUpUserSettingsCache(userId, request.targetCardLast4(), request.executionMode().name(), rules);
                }
            });
        } catch (DataIntegrityViolationException e) {
            throw new OnboardingAlreadyCompletedException();
        }
    }

    private void saveLinkedAccount(User user, OnboardingRequest dto) {
        LinkedAccount account = LinkedAccount.builder()
                .user(user)
                .kisAppKey(dto.kisAppKey())
                .kisAppSecret(dto.kisAppSecret())
                .kisAccountNum(dto.kisAccountNum())
                .targetCardCompany(dto.targetCardCompany())
                .targetCardLast4(dto.targetCardLast4())
                .build();
        linkedAccountRepository.save(account);
    }

    private void saveInvestmentProfile(User user, OnboardingRequest dto) {
        InvestmentProfile profile = InvestmentProfile.builder()
                .user(user)
                .riskTolerance(dto.riskTolerance())
                .investmentTerm(dto.investmentTerm())
                .investmentStyle(dto.investmentStyle())
                .preferredTheme(dto.preferredTheme())
                .stockCapPreference(dto.stockCapPreference())
                .marketPreference(dto.marketPreference())
                .esgFocus(dto.esgFocus())
                .sinIndustryFilter(dto.sinIndustryFilter())
                .returnPreference(dto.returnPreference())
                .diversificationType(dto.diversificationType())
                .build();
        investmentProfileRepository.save(profile);
    }

    private void saveInvestmentSettings(User user, OnboardingRequest dto) {
        InvestmentSettings settings = InvestmentSettings.builder()
                .user(user)
                .executionMode(dto.executionMode())
                .build();
        investmentSettingsRepository.save(settings);
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

    public void warmUpUserSettingsCache(Long userId, String targetCardLast4, String executionMode, List<CategorySpareChangeRule> rules) {
        String redisKey = USER_SETTINGS_CACHE_PREFIX + userId;
        Map<String, String> cacheData = new HashMap<>();

        cacheData.put("targetCardLast4", targetCardLast4);
        cacheData.put("executionMode", executionMode);

        rules.forEach(rule ->
                cacheData.put(rule.getCategory().name(), rule.getRuleType().name())
        );

        redisTemplate.opsForHash().putAll(redisKey, cacheData);
    }
}