package com.tikkle.settings.service;

import com.tikkle.investment.entity.InvestmentSettings;
import com.tikkle.investment.entity.enums.ExecutionMode;
import com.tikkle.investment.repository.InvestmentSettingsRepository;
import com.tikkle.payment.entity.CategorySpareChangeRule;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.repository.CategorySpareChangeRuleRepository;
import com.tikkle.settings.dto.request.UpdateExecutionModeRequest;
import com.tikkle.settings.dto.request.UpdateSpareChangeRulesRequest;
import com.tikkle.settings.dto.response.SettingsResponse;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.UserStatus;
import com.tikkle.user.exception.UserNotFoundException;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingsService {
    private final UserRepository userRepository;
    private final InvestmentSettingsRepository investmentSettingsRepository;
    private final CategorySpareChangeRuleRepository categorySpareChangeRuleRepository;
    private final SettingsCacheManager settingsCacheManager;

    public SettingsResponse getSettings(String email) {
        User user = findActiveUserByEmail(email);
        Long userId = user.getId();

        ExecutionMode executionMode = investmentSettingsRepository.findByUserId(userId)
                .map(InvestmentSettings::getExecutionMode)
                .orElse(ExecutionMode.MANUAL);

        List<SettingsResponse.CategoryRule> spareChangeRules = categorySpareChangeRuleRepository.findByUserId(userId).stream()
                .map(rule -> new SettingsResponse.CategoryRule(rule.getCategory(), rule.getRuleType()))
                .toList();

        return new SettingsResponse(executionMode, spareChangeRules);
    }

    @Transactional
    public void updateExecutionMode(String email, UpdateExecutionModeRequest request) {
        User user = findActiveUserByEmail(email);
        Long userId = user.getId();
        ExecutionMode executionMode = request.executionMode();

        investmentSettingsRepository.findByUserId(userId)
                .ifPresentOrElse(
                        settings -> settings.changeMode(executionMode),
                        () -> investmentSettingsRepository.save(InvestmentSettings.builder()
                                .user(user)
                                .executionMode(executionMode)
                                .build()));

        syncAfterCommit(() -> settingsCacheManager.updateExecutionMode(userId, executionMode));
    }

    @Transactional
    public void updateSpareChangeRules(String email, UpdateSpareChangeRulesRequest request) {
        User user = findActiveUserByEmail(email);
        Long userId = user.getId();

        Map<PaymentCategory, CategorySpareChangeRule> existing = categorySpareChangeRuleRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(CategorySpareChangeRule::getCategory, Function.identity()));

        List<CategorySpareChangeRule> changed = new ArrayList<>();
        for (UpdateSpareChangeRulesRequest.RuleItem item : request.rules()) {
            CategorySpareChangeRule rule = existing.get(item.category());
            if (rule != null) {
                rule.change(item.ruleType());
            } else {
                rule = categorySpareChangeRuleRepository.save(CategorySpareChangeRule.builder()
                        .user(user)
                        .category(item.category())
                        .ruleType(item.ruleType())
                        .build());
            }
            changed.add(rule);
        }

        syncAfterCommit(() -> settingsCacheManager.updateSpareChangeRules(userId, changed));
    }

    private User findActiveUserByEmail(String email) {
        return userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE)
                .orElseThrow(UserNotFoundException::new);
    }

    private void syncAfterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (Exception e) {
                    log.error("커밋 후 설정 캐시 동기화에 실패했습니다. DB 변경은 이미 반영되었습니다.", e);
                }
            }
        });
    }
}