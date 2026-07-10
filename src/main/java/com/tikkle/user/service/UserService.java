package com.tikkle.user.service;

import com.tikkle.investment.repository.InvestmentProfileRepository;
import com.tikkle.user.dto.response.UserResponse;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.UserStatus;
import com.tikkle.user.exception.UserNotFoundException;
import com.tikkle.user.repository.LinkedAccountRepository;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final InvestmentProfileRepository investmentProfileRepository;

    public UserResponse getMe(Long userId) {
        final User user = findActiveUserById(userId);
        
        boolean hasInvestmentProfile = false;
        boolean hasKbankAccount = false;
        boolean hasUpbitKey = false;

        hasInvestmentProfile = investmentProfileRepository.findByUserId(userId)
                .map(profile -> profile.getRiskTolerance() != null) // 하나라도 설정되었는지 확인
                .orElse(false);

        LinkedAccount account = linkedAccountRepository.findByUserId(userId).orElse(null);
        if (account != null) {
            hasKbankAccount = account.getTargetCardCompany() != null;
            hasUpbitKey = account.getUpbitAccessKey() != null && account.isUpbitKeyValid();
        }

        return UserResponse.from(user, hasInvestmentProfile, hasKbankAccount, hasUpbitKey);
    }

    /**
     * 유저 본인을 탈퇴 처리합니다. (상태값을 WITHDRAWN으로 변경)
     * @param userId 탈퇴할 유저 ID
     */
    @Transactional
    public void withdrawMe(Long userId) {
        findActiveUserById(userId).withdraw();
    }

    private User findActiveUserById(Long userId) {
        return userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(UserNotFoundException::new);
    }
}