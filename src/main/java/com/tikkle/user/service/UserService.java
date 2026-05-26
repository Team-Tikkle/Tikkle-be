package com.tikkle.user.service;

import com.tikkle.user.dto.request.CreateUserRequest;
import com.tikkle.user.dto.request.UpdateUserRequest;
import com.tikkle.user.dto.response.UserResponse;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.UserStatus;
import com.tikkle.user.repository.UserRepository;
import com.tikkle.user.exception.DuplicateEmailException;
import com.tikkle.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException();
        }
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .status(UserStatus.ACTIVE)
                .build();
        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse getUser(Long id) {
        return UserResponse.from(findActiveUser(id));
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = findActiveUser(id);
        user.update(request.getName(), request.getPhone());
        return UserResponse.from(user);
    }

    @Transactional
    public void withdrawUser(Long id) {
        findActiveUser(id).withdraw();
    }

    private User findActiveUser(Long id) {
        return userRepository.findByIdAndStatus(id, UserStatus.ACTIVE)
                .orElseThrow(UserNotFoundException::new);
    }
}
