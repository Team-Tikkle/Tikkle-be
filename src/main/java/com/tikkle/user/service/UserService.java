package com.tikkle.user.service;

import com.tikkle.user.dto.request.UpdateUserRequest;
import com.tikkle.user.dto.response.UserResponse;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.UserStatus;
import com.tikkle.user.repository.LinkedAccountRepository;
import com.tikkle.user.repository.UserRepository;
import com.tikkle.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;
    private final LinkedAccountRepository linkedAccountRepository;

    public UserResponse getMe(String email) {
        final User user = findActiveUserByEmail(email);
        return UserResponse.from(user, isNewUser(user.getId()));
    }

    @Transactional
    public UserResponse updateMe(String email, UpdateUserRequest request) {
        final User user = findActiveUserByEmail(email);
        user.update(request.name());
        return UserResponse.from(user, isNewUser(user.getId()));
    }

    @Transactional
    public void withdrawMe(String email) {
        findActiveUserByEmail(email).withdraw();
    }

    private User findActiveUserByEmail(String email) {
        return userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE)
                .orElseThrow(UserNotFoundException::new);
    }

    // 온보딩 미완료 시 신규 유저로 간주한다. 온보딩은 단일 트랜잭션으로 처리되므로 LinkedAccount 존재 여부로 판단한다.
    private boolean isNewUser(Long userId) {
        return linkedAccountRepository.findByUserId(userId).isEmpty();
    }
}