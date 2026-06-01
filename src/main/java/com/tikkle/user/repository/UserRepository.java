package com.tikkle.user.repository;

import com.tikkle.user.entity.User;
import com.tikkle.user.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    Optional<User> findByEmailAndStatus(String email, UserStatus status);
    Optional<User> findByIdAndStatus(Long id, UserStatus status);
}