package com.tikkle.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ConsolePushNotificationService implements PushNotificationService {

    @Override
    public void sendPush(Long userId, String title, String body) {
        log.info("[PushNotificationService] FCM 알림 전송 (콘솔 모의) - userId: {}, title: {}, body: {}", userId, title, body);
    }
}
