package com.tikkle.notification.service;

import com.tikkle.notification.entity.DeviceToken;
import com.tikkle.notification.repository.DeviceTokenRepository;
import com.tikkle.user.entity.User;
import com.tikkle.user.exception.UserNotFoundException;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FCM 디바이스 토큰의 등록/해제를 담당하는 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceTokenService {
    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;

    /**
     * 디바이스 토큰을 멱등하게 등록합니다.
     * 이미 다른 유저에게 등록된 토큰이면 현재 유저로 소유권을 이전합니다(기기 양도/재로그인 대응).
     *
     * @param userId 로그인한 유저 ID
     * @param fcmToken FCM 디바이스 토큰
     */
    @Transactional
    public void registerToken(Long userId, String fcmToken) {
        deviceTokenRepository.findByFcmToken(fcmToken).ifPresentOrElse(
                existing -> {
                    if (!existing.getUser().getId().equals(userId)) {
                        existing.reassignTo(findUserById(userId));
                        log.info("[DeviceTokenService] 디바이스 토큰 소유권 이전 - userId: {}", userId);
                    }
                },
                () -> {
                    deviceTokenRepository.save(DeviceToken.builder()
                            .user(findUserById(userId))
                            .fcmToken(fcmToken)
                            .build());
                    log.info("[DeviceTokenService] 디바이스 토큰 신규 등록 - userId: {}", userId);
                }
        );
    }

    /**
     * 디바이스 토큰을 멱등하게 해제합니다. 인증 유저 소유의 토큰만 삭제하며,
     * 토큰이 없거나 다른 유저 소유이면 예외 없이 정상 종료합니다.
     *
     * @param userId 로그인한 유저 ID
     * @param fcmToken 해제할 FCM 디바이스 토큰
     */
    @Transactional
    public void unregisterToken(Long userId, String fcmToken) {
        deviceTokenRepository.deleteByFcmTokenAndUserId(fcmToken, userId);
        log.info("[DeviceTokenService] 디바이스 토큰 해제 완료 - userId: {}", userId);
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }
}