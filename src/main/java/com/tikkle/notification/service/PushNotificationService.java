package com.tikkle.notification.service;

public interface PushNotificationService {
    /**
     * 사용자에게 푸시 알림을 발송합니다.
     *
     * @param userId 알림을 받을 사용자 ID
     * @param title 알림 제목
     * @param body 알림 내용
     */
    void sendPush(Long userId, String title, String body);
}
