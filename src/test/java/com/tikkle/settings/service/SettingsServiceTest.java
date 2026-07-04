package com.tikkle.settings.service;

import com.tikkle.payment.entity.CategorySpareChangeRule;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.RuleType;
import com.tikkle.payment.repository.CategorySpareChangeRuleRepository;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private UserRepository userRepository;
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
    @DisplayName("getSettings - DB에 저장된 카테고리 룰만 반환한다")
    void getSettings_returnsStoredRules() {
        givenActiveUser();
        given(categorySpareChangeRuleRepository.findByUserId(USER_ID)).willReturn(
                List.of(rule(PaymentCategory.CAFE, RuleType.PERCENT_10)));

        SettingsResponse response = settingsService.getSettings(EMAIL);

        assertThat(response.spareChangeRules()).hasSize(1);

        Map<PaymentCategory, RuleType> result = response.spareChangeRules().stream()
                .collect(Collectors.toMap(SettingsResponse.CategoryRule::category, SettingsResponse.CategoryRule::ruleType));
        assertThat(result.get(PaymentCategory.CAFE)).isEqualTo(RuleType.PERCENT_10);
        assertThat(result).doesNotContainKey(PaymentCategory.SHOPPING);
    }


    @Test
    @DisplayName("getSettings - 활성 유저가 없으면 UserNotFoundException")
    void getSettings_userNotFound() {
        given(userRepository.findByEmailAndStatus(EMAIL, UserStatus.ACTIVE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settingsService.getSettings(EMAIL))
                .isInstanceOf(UserNotFoundException.class);
    }


    @Test
    @DisplayName("updateSpareChangeRules - 기존은 변경, 없는 카테고리는 생성하고 변경분만 캐시에 반영한다")
    void updateSpareChangeRules_upsertsAndSyncsCache() {
        givenActiveUser();
        CategorySpareChangeRule cafe = rule(PaymentCategory.CAFE, RuleType.PERCENT_10);
        given(categorySpareChangeRuleRepository.findByUserId(USER_ID)).willReturn(List.of(cafe));
        given(categorySpareChangeRuleRepository.save(any(CategorySpareChangeRule.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        UpdateSpareChangeRulesRequest request = new UpdateSpareChangeRulesRequest(List.of(
                new UpdateSpareChangeRulesRequest.RuleItem(PaymentCategory.CAFE, RuleType.ROUND_UP_50000),
                new UpdateSpareChangeRulesRequest.RuleItem(PaymentCategory.SHOPPING, RuleType.ROUND_UP_10000)));

        runWithTransactionSync(() -> settingsService.updateSpareChangeRules(EMAIL, request));

        assertThat(cafe.getRuleType()).isEqualTo(RuleType.ROUND_UP_50000);
        verify(categorySpareChangeRuleRepository).save(any(CategorySpareChangeRule.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CategorySpareChangeRule>> captor = ArgumentCaptor.forClass(List.class);
        verify(settingsCacheManager).updateSpareChangeRules(eq(USER_ID), captor.capture());
        Map<PaymentCategory, RuleType> synced = captor.getValue().stream()
                .collect(Collectors.toMap(CategorySpareChangeRule::getCategory, CategorySpareChangeRule::getRuleType));
        assertThat(synced).containsEntry(PaymentCategory.CAFE, RuleType.ROUND_UP_50000)
                .containsEntry(PaymentCategory.SHOPPING, RuleType.ROUND_UP_10000);
    }

    private CategorySpareChangeRule rule(PaymentCategory category, RuleType ruleType) {
        return CategorySpareChangeRule.builder().user(user).category(category).ruleType(ruleType).build();
    }

    /**
     * 서비스의 afterCommit 콜백을 검증하기 위해 트랜잭션 동기화를 명시적으로 활성화한 뒤
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