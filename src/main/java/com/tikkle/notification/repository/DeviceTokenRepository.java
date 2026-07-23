package com.tikkle.notification.repository;

import com.tikkle.notification.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    Optional<DeviceToken> findByFcmToken(String fcmToken);

    List<DeviceToken> findByUserId(Long userId);

    void deleteByFcmToken(String fcmToken);

    // 회원 탈퇴 시 소유 토큰 일괄 삭제 (DEVICE_TOKENS.user_id → USERS FK)
    void deleteByUserId(Long userId);
}