package com.tikkle.payment.interceptor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * HMAC SHA256 알고리즘을 사용하여 결제 스크래핑 요청의 서명 유효성을 검증하는 클래스입니다.
 */
@Component
public class SignatureValidator {
    private final String secretKey;

    public SignatureValidator(@Value("${tikkle.payment.secret-key}") String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isValid(String payload, String timestamp, String signature) {
        try {
            String data = payload + timestamp;
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);

            byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
            byte[] decodedSignature = Base64.getDecoder().decode(signature);
            return MessageDigest.isEqual(hash, decodedSignature);
        } catch (NoSuchAlgorithmException | InvalidKeyException | IllegalArgumentException e) {
            return false;
        }
    }
}