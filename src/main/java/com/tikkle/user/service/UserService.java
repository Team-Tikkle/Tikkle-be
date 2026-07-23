package com.tikkle.user.service;

import com.tikkle.auth.repository.RefreshTokenRepository;
import com.tikkle.investment.repository.InvestmentProfileRepository;
import com.tikkle.investment.repository.PortfolioRepository;
import com.tikkle.notification.repository.DeviceTokenRepository;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.settings.repository.CategorySpareChangeRuleRepository;
import com.tikkle.settings.service.SettingsCacheManager;
import com.tikkle.user.dto.response.UserResponse;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.entity.User;
import com.tikkle.user.exception.UserNotFoundException;
import com.tikkle.user.repository.LinkedAccountRepository;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final InvestmentProfileRepository investmentProfileRepository;
    private final PortfolioRepository portfolioRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final CategorySpareChangeRuleRepository categorySpareChangeRuleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SettingsCacheManager settingsCacheManager;
    private final DeviceTokenRepository deviceTokenRepository;

    public UserResponse getMe(Long userId) {
        final User user = findUserById(userId);

        boolean hasInvestmentProfile = false;
        boolean hasKbankAccount = false;
        boolean hasUpbitKey = false;

        hasInvestmentProfile = investmentProfileRepository.findByUserId(userId)
                .map(profile -> profile.getRiskTolerance() != null) // 하나라도 설정되었는지 확인
                .orElse(false);

        LinkedAccount account = linkedAccountRepository.findByUserId(userId).orElse(null);
        if (account != null) {
            hasKbankAccount = account.getTargetCardCompany() != null;
            hasUpbitKey = account.getUpbitAccessKey() != null;
        }

        return UserResponse.from(user, hasInvestmentProfile, hasKbankAccount, hasUpbitKey);
    }

    /**
     * 유저 본인을 탈퇴 처리합니다.
     * 논리 삭제가 아닌 완전 삭제이며, 해당 유저의 모든 데이터(투자 성향, 잔돈 규칙, 연동 계좌,
     * 보유 종목, 결제 원장)와 Redis 세션·설정 캐시를 함께 제거합니다.
     * 휴대폰 번호가 즉시 회수되므로 동일 번호로 곧바로 재가입할 수 있습니다.
     *
     * @param userId 탈퇴할 유저 ID
     */
    @Transactional
    public void withdrawMe(Long userId) {
        final User user = findUserById(userId);
        log.info("[UserService] 회원 탈퇴(완전 삭제) 처리 시작 - userId: {}", userId);

        // 1. 결제 원장 (USERS에 FK가 없는 스칼라 참조라 명시적으로 지워야 한다)
        paymentEventRepository.deleteAllByUserId(userId);

        // 2. USERS를 참조하는 자식 데이터 (FK 제약 때문에 USERS보다 먼저 삭제)
        portfolioRepository.deleteByUserId(userId);
        categorySpareChangeRuleRepository.deleteByUserId(userId);
        linkedAccountRepository.deleteByUserId(userId);
        deviceTokenRepository.deleteByUserId(userId);

        // 3. 투자 성향은 @ElementCollection(INVESTMENT_PROFILE_THEMES)을 함께 정리해야 하므로
        //    벌크 삭제가 아닌 엔티티 삭제로 처리하여 Hibernate가 테마 행까지 지우게 한다
        investmentProfileRepository.findByUserId(userId)
                .ifPresent(investmentProfileRepository::delete);

        // 4. 유저 본체
        userRepository.delete(user);

        // 5. Redis 잔여 데이터 (트랜잭션 롤백 대상이 아니므로 커밋 후 정리)
        evictRedisAfterCommit(userId);

        log.info("[UserService] 회원 탈퇴(완전 삭제) 처리 완료 - userId: {}", userId);
    }

    /**
     * DB 삭제가 커밋된 뒤에만 Redis 세션·설정 캐시를 제거합니다.
     * 커밋 전에 지우면 트랜잭션이 롤백됐을 때 멀쩡한 유저의 세션만 날아갑니다.
     */
    private void evictRedisAfterCommit(Long userId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    refreshTokenRepository.deleteById(userId);
                    settingsCacheManager.evict(userId);
                } catch (Exception e) {
                    // 캐시는 TTL로도 소멸하고 DB 유저가 이미 없어 재사용될 수 없으므로 로깅만 한다
                    log.error("[UserService] 탈퇴 후 Redis 잔여 데이터 정리 실패 - userId: {}", userId, e);
                }
            }
        });
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }
}