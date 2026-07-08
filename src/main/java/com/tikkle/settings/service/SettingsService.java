package com.tikkle.settings.service;

import com.tikkle.payment.entity.CategorySpareChangeRule;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.repository.CategorySpareChangeRuleRepository;
import com.tikkle.settings.dto.request.UpdateInvestmentStatusRequest;
import com.tikkle.settings.dto.request.UpdateLinkedAccountRequest;
import com.tikkle.settings.dto.request.UpdateSpareChangeRulesRequest;
import com.tikkle.settings.dto.response.SettingsResponse;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.exception.LinkedAccountNotFoundException;
import com.tikkle.user.repository.LinkedAccountRepository;
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

/**
 * 사용자 설정(잔돈 규칙, 투자 활성화 여부, 연동 계좌 등)을 관리하는 비즈니스 로직 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingsService {
    private final UserRepository userRepository;
    private final CategorySpareChangeRuleRepository categorySpareChangeRuleRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final SettingsCacheManager settingsCacheManager;

    public SettingsResponse getSettings(Long userId) {
        List<SettingsResponse.CategoryRule> spareChangeRules = categorySpareChangeRuleRepository.findByUserId(userId).stream()
                .map(rule -> new SettingsResponse.CategoryRule(rule.getCategory(), rule.getRuleType()))
                .toList();

        LinkedAccount account = linkedAccountRepository.findByUserId(userId)
                .orElseThrow(LinkedAccountNotFoundException::new);

        return new SettingsResponse(spareChangeRules, account.isInvestmentEnabled());
    }


    @Transactional
    public void updateSpareChangeRules(Long userId, UpdateSpareChangeRulesRequest request) {
        log.info("[SettingsService] 카테고리 잔돈 규칙 변경 처리 - userId: {}", userId);
        Map<PaymentCategory, CategorySpareChangeRule> existing = categorySpareChangeRuleRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(CategorySpareChangeRule::getCategory, Function.identity()));

        List<CategorySpareChangeRule> changed = new ArrayList<>();
        for (UpdateSpareChangeRulesRequest.RuleItem item : request.rules()) {
            CategorySpareChangeRule rule = existing.get(item.category());
            if (rule != null) {
                rule.change(item.ruleType());
            } else {
                rule = categorySpareChangeRuleRepository.save(CategorySpareChangeRule.builder()
                        .user(userRepository.getReferenceById(userId))
                        .category(item.category())
                        .ruleType(item.ruleType())
                        .build());
            }
            changed.add(rule);
        }

        syncAfterCommit(() -> settingsCacheManager.updateSpareChangeRules(userId, changed));
    }

    @Transactional
    public void updateLinkedAccount(Long userId, UpdateLinkedAccountRequest request) {
        log.info("[SettingsService] 업비트 계정 연동 정보 변경 처리 - userId: {}", userId);
        LinkedAccount account = linkedAccountRepository.findByUserId(userId)
                .orElseThrow(LinkedAccountNotFoundException::new);

        account.updateUpbitCredentials(request.upbitAccessKey(), request.upbitSecretKey());
    }

    @Transactional
    public void updateInvestmentStatus(Long userId, UpdateInvestmentStatusRequest request) {
        log.info("[SettingsService] 자동 투자 활성화 상태 변경 처리 - userId: {}, isInvestmentEnabled: {}", userId, request.isInvestmentEnabled());
        LinkedAccount account = linkedAccountRepository.findByUserId(userId)
                .orElseThrow(LinkedAccountNotFoundException::new);

        account.updateInvestmentStatus(request.isInvestmentEnabled());

        syncAfterCommit(() -> settingsCacheManager.updateInvestmentStatus(userId, request.isInvestmentEnabled()));
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