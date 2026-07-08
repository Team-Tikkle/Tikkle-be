package com.tikkle.user.service;

import com.tikkle.user.dto.request.UpdateUserRequest;
import com.tikkle.user.dto.response.UserResponse;
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

    public UserResponse getMe(Long userId) {
        final User user = findActiveUserById(userId);
        return UserResponse.from(user, isNewUser(user.getId()));
    }

    /**
     * 유저 정보를 수정합니다.
     * @param userId 수정할 유저 ID
     * @param request 수정 요청 정보
     * @return 수정된 유저 정보
     */
    @Transactional
    public UserResponse updateMe(Long userId, UpdateUserRequest request) {
        log.info("[UserService] 회원 정보 수정 시작 - userId: {}", userId);
        final User user = findActiveUserById(userId);
        user.update(request.name());
        log.info("[UserService] 회원 정보 수정 완료 - userId: {}", userId);
        return UserResponse.from(user, isNewUser(user.getId()));
    }

    /**
     * 유저 본인을 탈퇴 처리합니다. (상태값을 WITHDRAWN으로 변경)
     * @param userId 탈퇴할 유저 ID
     */
    @Transactional
    public void withdrawMe(Long userId) {
        log.info("[UserService] 회원 탈퇴 처리 시작 - userId: {}", userId);
        findActiveUserById(userId).withdraw();
        log.info("[UserService] 회원 탈퇴 처리 완료 - userId: {}", userId);
    }

    private User findActiveUserById(Long userId) {
        return userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(UserNotFoundException::new);
    }

    // 온보딩 미완료 시 신규 유저로 간주한다. 온보딩은 단일 트랜잭션으로 처리되므로 LinkedAccount 존재 여부로 판단한다.
    private boolean isNewUser(Long userId) {
        return linkedAccountRepository.findByUserId(userId).isEmpty();
    }
}