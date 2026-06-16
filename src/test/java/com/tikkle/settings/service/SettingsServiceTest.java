package com.tikkle.settings.service;

import com.tikkle.investment.entity.InvestmentSettings;
import com.tikkle.investment.entity.enums.ExecutionMode;
import com.tikkle.investment.repository.InvestmentSettingsRepository;
import com.tikkle.payment.entity.CategorySpareChangeRule;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.RuleType;
import com.tikkle.payment.repository.CategorySpareChangeRuleRepository;
import com.tikkle.settings.dto.request.UpdateExecutionModeRequest;
import com.tikkle.settings.dto.request.UpdateSpareChangeRulesRequest;
import com.tikkle.settings.dto.response.SettingsResponse;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.UserStatus;
import com.tikkle.user.exception.UserNotFoundException;
import com.tikkle.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private InvestmentSettingsRepository investmentSettingsRepository;
    @Mock
    private CategorySpareChangeRuleRepository categorySpareChangeRuleRepository;
    @Mock
    private SettingsCacheManager settingsCacheManager;

    @InjectMocks
    private SettingsService settingsService;

    private static final String EMAIL = "user@tikkle.app";
    private static final Long USER_ID = 1L;
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().email(EMAIL).build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
    }

    private void givenActiveUser() {
        given(userRepository.findByEmailAndStatus(EMAIL, UserStatus.ACTIVE)).willReturn(Optional.of(user));
    }

    @Test
    @DisplayName("getSettings - 미설정 카테고리는 NONE, 미설정 매매방식은 MANUAL로 채운다")
    void getSettings_fillsDefaults() {
        givenActiveUser();
        given(investmentSettingsRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(categorySpareChangeRuleRepository.findByUserId(USER_ID)).willReturn(
                List.of(rule(PaymentCategory.CAFE, RuleType.PERCENT_5)));

        SettingsResponse response = settingsService.getSettings(EMAIL);

        assertThat(response.executionMode()).isEqualTo(ExecutionMode.MANUAL);
        assertThat(response.spareChangeRules()).hasSize(PaymentCategory.values().length);

        Map<PaymentCategory, RuleType> result = response.spareChangeRules().stream()
                .collect(Collectors.toMap(SettingsResponse.CategoryRule::category, SettingsResponse.CategoryRule::ruleType));
        assertThat(result.get(PaymentCategory.CAFE)).isEqualTo(RuleType.PERCENT_5);
        assertThat(result.get(PaymentCategory.SHOPPING)).isEqualTo(RuleType.NONE);
    }

    @Test
    @DisplayName("getSettings - 저장된 매매방식 값을 그대로 반환한다")
    void getSettings_returnsStoredValues() {
        givenActiveUser();
        given(investmentSettingsRepository.findByUserId(USER_ID)).willReturn(
                Optional.of(InvestmentSettings.builder().user(user).executionMode(ExecutionMode.AUTO).build()));
        given(categorySpareChangeRuleRepository.findByUserId(USER_ID)).willReturn(List.of());

        SettingsResponse response = settingsService.getSettings(EMAIL);

        assertThat(response.executionMode()).isEqualTo(ExecutionMode.AUTO);
    }

    @Test
    @DisplayName("getSettings - 활성 유저가 없으면 UserNotFoundException")
    void getSettings_userNotFound() {
        given(userRepository.findByEmailAndStatus(EMAIL, UserStatus.ACTIVE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settingsService.getSettings(EMAIL))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("updateExecutionMode - 기존 설정은 변경하고 커밋 후 캐시에 동기화한다")
    void updateExecutionMode_updatesExistingAndSyncsCache() {
        givenActiveUser();
        InvestmentSettings settings = InvestmentSettings.builder().user(user).executionMode(ExecutionMode.MANUAL).build();
        given(investmentSettingsRepository.findByUserId(USER_ID)).willReturn(Optional.of(settings));

        runWithTransactionSync(() ->
                settingsService.updateExecutionMode(EMAIL, new UpdateExecutionModeRequest(ExecutionMode.AUTO)));

        assertThat(settings.getExecutionMode()).isEqualTo(ExecutionMode.AUTO);
        verify(investmentSettingsRepository, never()).save(any());
        verify(settingsCacheManager).updateExecutionMode(USER_ID, ExecutionMode.AUTO);
    }

    @Test
    @DisplayName("updateExecutionMode - 설정이 없으면 새로 저장한다")
    void updateExecutionMode_createsWhenAbsent() {
        givenActiveUser();
        given(investmentSettingsRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        runWithTransactionSync(() ->
                settingsService.updateExecutionMode(EMAIL, new UpdateExecutionModeRequest(ExecutionMode.AUTO)));

        verify(investmentSettingsRepository).save(any(InvestmentSettings.class));
        verify(settingsCacheManager).updateExecutionMode(USER_ID, ExecutionMode.AUTO);
    }

    @Test
    @DisplayName("updateSpareChangeRules - 기존은 변경, 없는 카테고리는 생성하고 변경분만 캐시에 반영한다")
    void updateSpareChangeRules_upsertsAndSyncsCache() {
        givenActiveUser();
        CategorySpareChangeRule cafe = rule(PaymentCategory.CAFE, RuleType.PERCENT_5);
        given(categorySpareChangeRuleRepository.findByUserId(USER_ID)).willReturn(List.of(cafe));
        given(categorySpareChangeRuleRepository.save(any(CategorySpareChangeRule.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        UpdateSpareChangeRulesRequest request = new UpdateSpareChangeRulesRequest(List.of(
                new UpdateSpareChangeRulesRequest.RuleItem(PaymentCategory.CAFE, RuleType.ROUND_UP_5000),
                new UpdateSpareChangeRulesRequest.RuleItem(PaymentCategory.SHOPPING, RuleType.NONE)));

        runWithTransactionSync(() -> settingsService.updateSpareChangeRules(EMAIL, request));

        assertThat(cafe.getRuleType()).isEqualTo(RuleType.ROUND_UP_5000);
        verify(categorySpareChangeRuleRepository).save(any(CategorySpareChangeRule.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CategorySpareChangeRule>> captor = ArgumentCaptor.forClass(List.class);
        verify(settingsCacheManager).updateSpareChangeRules(eq(USER_ID), captor.capture());
        Map<PaymentCategory, RuleType> synced = captor.getValue().stream()
                .collect(Collectors.toMap(CategorySpareChangeRule::getCategory, CategorySpareChangeRule::getRuleType));
        assertThat(synced).containsEntry(PaymentCategory.CAFE, RuleType.ROUND_UP_5000)
                .containsEntry(PaymentCategory.SHOPPING, RuleType.NONE);
    }

    private CategorySpareChangeRule rule(PaymentCategory category, RuleType ruleType) {
        return CategorySpareChangeRule.builder().user(user).category(category).ruleType(ruleType).build();
    }

    /**
     * 서비스의 afterCommit 콜백을 검증하기 위해 트랜잭션 동기화를 수동으로 활성화한 뒤
     * 등록된 콜백을 직접 트리거한다.
     */
    private void runWithTransactionSync(Runnable action) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            action.run();
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
}
