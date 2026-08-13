package com.tikkle.settings.service;

import com.tikkle.settings.entity.CategorySpareChangeRule;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.RuleType;
import com.tikkle.settings.repository.CategorySpareChangeRuleRepository;
import com.tikkle.settings.dto.request.UpdateInvestmentProfileRequest;
import com.tikkle.settings.dto.request.UpdateKbankAccountRequest;
import com.tikkle.settings.dto.request.UpdateUpbitKeyRequest;
import com.tikkle.settings.dto.request.UpdateInvestmentStatusRequest;
import com.tikkle.settings.dto.request.UpdateSpareChangeRulesRequest;
import com.tikkle.settings.dto.response.SettingsResponse;
import com.tikkle.user.entity.LinkedAccount;

import com.tikkle.user.exception.LinkedAccountNotFoundException;

import com.tikkle.user.repository.LinkedAccountRepository;
import com.tikkle.user.repository.UserRepository;
import com.tikkle.investment.entity.InvestmentProfile;
import com.tikkle.investment.exception.InvestmentProfileNotFoundException;
import com.tikkle.investment.repository.InvestmentProfileRepository;
import com.tikkle.upbit.service.UpbitKeyValidationService;
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
    private final InvestmentProfileRepository investmentProfileRepository;
    private final SettingsCacheManager settingsCacheManager;
    private final UpbitKeyValidationService upbitKeyValidationService;

    /**
     * 사용자의 설정(잔돈 규칙, 투자 활성화 여부 등)을 조회합니다.
     *
     * @param userId 조회할 사용자 ID
     * @return 설정 정보 응답 DTO
     */
    public SettingsResponse getSettings(Long userId) {
        Map<PaymentCategory, RuleType> dbRules = categorySpareChangeRuleRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(CategorySpareChangeRule::getCategory, CategorySpareChangeRule::getRuleType));

        List<SettingsResponse.CategoryRule> spareChangeRules = java.util.Arrays.stream(PaymentCategory.values())
                .map(category -> new SettingsResponse.CategoryRule(category, dbRules.get(category)))
                .toList();

        LinkedAccount account = linkedAccountRepository.findByUserId(userId).orElse(null);
        boolean isEnabled = (account != null) && account.isInvestmentEnabled();
        
        SettingsResponse.LinkedAccountInfo linkedAccountInfo = null;
        if (account != null) {
            linkedAccountInfo = new SettingsResponse.LinkedAccountInfo(
                    account.getTargetCardCompany(),
                    account.getTargetCardLast4(),
                    account.getTwoFactorProvider()
            );
        }

        InvestmentProfile profile = investmentProfileRepository.findByUserId(userId).orElse(null);
        SettingsResponse.InvestmentProfileInfo investmentProfileInfo = null;
        if (profile != null) {
            investmentProfileInfo = new SettingsResponse.InvestmentProfileInfo(
                    profile.getRiskTolerance(),
                    profile.getTrendSensitivity(),
                    profile.getCryptoThemes(),
                    profile.getDiversificationType(),
                    profile.getMemeAcceptance()
            );
        }

        return new SettingsResponse(spareChangeRules, isEnabled, linkedAccountInfo, investmentProfileInfo);
    }

    /**
     * 카테고리별 잔돈 규칙을 변경하거나 새로 등록합니다.
     *
     * @param userId 변경할 사용자 ID
     * @param request 변경할 잔돈 규칙 목록
     */
    @Transactional
    public void updateSpareChangeRules(Long userId, UpdateSpareChangeRulesRequest request) {
        log.info("[SettingsService] 카테고리 잔돈 규칙 변경 처리 - userId: {}", userId);
        Map<PaymentCategory, CategorySpareChangeRule> existing = categorySpareChangeRuleRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(CategorySpareChangeRule::getCategory, Function.identity()));

        for (UpdateSpareChangeRulesRequest.RuleItem item : request.rules()) {
            CategorySpareChangeRule rule = existing.get(item.category());
            if (rule != null) {
                rule.change(item.ruleType());
            } else {
                categorySpareChangeRuleRepository.save(CategorySpareChangeRule.builder()
                        .user(userRepository.getReferenceById(userId))
                        .category(item.category())
                        .ruleType(item.ruleType())
                        .build());
            }
        }

        evictCacheAfterCommit(userId);
    }

    /**
     * 사용자의 투자 성향 정보를 수정합니다.
     *
     * @param userId 변경할 사용자 ID
     * @param request 투자 성향 수정 요청 정보
     */
    @Transactional
    public void updateInvestmentProfile(Long userId, UpdateInvestmentProfileRequest request) {
        log.info("[SettingsService] 투자 성향 정보 수정 처리 - userId: {}", userId);
        InvestmentProfile profile = investmentProfileRepository.findByUserId(userId)
                .orElseThrow(InvestmentProfileNotFoundException::new);

        profile.updateProfile(
                request.riskTolerance(),
                request.trendSensitivity(),
                request.cryptoThemes() != null ? new ArrayList<>(request.cryptoThemes()) : null,
                request.diversificationType(),
                request.memeAcceptance()
        );
    }

    /**
     * 케이뱅크 연동 카드 정보를 수정합니다.
     *
     * @param userId 변경할 사용자 ID
     * @param request 케이뱅크 계좌/카드 수정 요청 정보
     */
    @Transactional
    public void updateKbankAccount(Long userId, UpdateKbankAccountRequest request) {
        log.info("[SettingsService] 케이뱅크(타겟카드) 계정 정보 수정 처리 - userId: {}", userId);
        LinkedAccount account = linkedAccountRepository.findByUserId(userId)
                .orElseThrow(LinkedAccountNotFoundException::new);

        account.updateKbankInfo("KBANK", request.targetCardLast4());
        evictCacheAfterCommit(userId);
    }

    /**
     * 업비트 API 키를 등록 또는 수정하고 유효성을 검증합니다.
     *
     * @param userId 변경할 사용자 ID
     * @param request 업비트 키 및 2차 인증 정보
     */
    @Transactional
    public void updateUpbitKey(Long userId, UpdateUpbitKeyRequest request) {
        log.info("[SettingsService] 업비트 API 키 수정 및 유효성 검증 시작 - userId: {}", userId);
        
        // 1. 엔티티 존재 여부 확인 (없으면 예외 발생)
        LinkedAccount account = linkedAccountRepository.findByUserId(userId)
                .orElseThrow(LinkedAccountNotFoundException::new);

        // 2. 업비트 키 유효성 검사
        upbitKeyValidationService.validateKeyOrThrow(request.upbitAccessKey(), request.upbitSecretKey());

        // 3. 검증 통과 후 업데이트
        account.updateUpbitCredentials(request.upbitAccessKey(), request.upbitSecretKey(), request.twoFactorProvider());
        
        log.info("[SettingsService] 업비트 API 키 수정 완료 - userId: {}", userId);
    }

    /**
     * 자동 투자 서비스의 활성화/비활성화 상태를 변경합니다.
     *
     * @param userId 변경할 사용자 ID
     * @param request 투자 상태 수정 요청 정보
     */
    @Transactional
    public void updateInvestmentStatus(Long userId, UpdateInvestmentStatusRequest request) {
        log.info("[SettingsService] 자동 투자 활성화 상태 변경 처리 - userId: {}, isInvestmentEnabled: {}", userId, request.isInvestmentEnabled());
        LinkedAccount account = linkedAccountRepository.findByUserId(userId)
                .orElseThrow(LinkedAccountNotFoundException::new);
        account.updateInvestmentStatus(request.isInvestmentEnabled());
        evictCacheAfterCommit(userId);
    }

    // DB 커밋 이후에만 캐시를 무효화한다(롤백 시 멀쩡한 캐시를 지우지 않도록)
    private void evictCacheAfterCommit(Long userId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    settingsCacheManager.evict(userId);
                } catch (Exception e) {
                    log.error("[SettingsService] 커밋 후 설정 캐시 무효화 실패 - userId: {}", userId, e);
                }
            }
        });
    }
}