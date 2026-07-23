package com.tikkle.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * FCM(Firebase Cloud Messaging) 발송을 위한 FirebaseApp/FirebaseMessaging 빈을 구성합니다.
 * 서비스 계정 키(JSON)는 리포지토리에 두지 않고 base64로 인코딩한 환경변수로만 주입합니다.
 * {@code tikkle.fcm.enabled=false}(기본값)이면 빈을 생성하지 않으므로,
 * 로컬 개발자는 Firebase 키 없이도 서버를 기동할 수 있습니다.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "tikkle.fcm.enabled", havingValue = "true")
public class FirebaseConfig {

    @Value("${tikkle.fcm.credentials-base64}")
    private String credentialsBase64;

    /**
     * base64로 주입된 서비스 계정 키를 복호화해 FirebaseApp을 초기화합니다.
     * 이미 초기화된 앱이 있으면 재사용하여 중복 초기화를 방지합니다.
     */
    @Bean
    public FirebaseApp firebaseApp() throws Exception {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        byte[] decoded = Base64.getDecoder().decode(credentialsBase64.getBytes(StandardCharsets.UTF_8));
        GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(decoded));

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();

        FirebaseApp app = FirebaseApp.initializeApp(options);
        log.info("[FirebaseConfig] FirebaseApp 초기화 완료 - FCM 발송 활성화");
        return app;
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}