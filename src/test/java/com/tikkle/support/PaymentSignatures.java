package com.tikkle.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * 결제 수신 요청의 서명을 테스트에서 만들 때 씁니다.
 * 앱이 하는 것과 같은 방식(본문 + 타임스탬프를 HMAC SHA-256 으로 서명해 Base64 로 인코딩)입니다.
 */
public final class PaymentSignatures {

    private static final String ALGORITHM = "HmacSHA256";

    private PaymentSignatures() {
    }

    public static String sign(String secretKey, String payload, String timestamp) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] hash = mac.doFinal((payload + timestamp).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("테스트 서명을 만들지 못했습니다.", e);
        }
    }
}
