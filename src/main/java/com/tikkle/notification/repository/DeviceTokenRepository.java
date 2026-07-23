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

    // FCM이 무효 판정한 죽은 토큰 정리용 (유저 무관, 전역 삭제)
    void deleteByFcmToken(String fcmToken);

    // 로그아웃 해제 — 인증 유저 소유의 토큰만 삭제(방어적 스코핑)
    void deleteByFcmTokenAndUserId(String fcmToken, Long userId);

    // 회원 탈퇴 시 소유 토큰 일괄 삭제 (DEVICE_TOKENS.user_id → USERS FK)
    void deleteByUserId(Long userId);
}