package com.tikkle.user.service;

import com.tikkle.user.dto.request.UpdateUserRequest;
import com.tikkle.user.dto.response.UserResponse;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.UserStatus;
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

    public UserResponse getMe(String email) {
        return UserResponse.from(findActiveUserByEmail(email));
    }

    @Transactional
    public UserResponse updateMe(String email, UpdateUserRequest request) {
        final User user = findActiveUserByEmail(email);
        user.update(request.name());
        return UserResponse.from(user);
    }

    @Transactional
    public void withdrawMe(String email) {
        findActiveUserByEmail(email).withdraw();
    }

    private User findActiveUserByEmail(String email) {
        return userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE)
                .orElseThrow(UserNotFoundException::new);
    }
}