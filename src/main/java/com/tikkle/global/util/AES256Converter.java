package com.tikkle.global.util;

import com.tikkle.global.exception.EncryptionFailedException;
import com.tikkle.global.exception.InvalidEncryptionKeyException;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * DB에 민감한 문자열을 저장할 때 AES-256 방식으로 암호화하고, 
 * 읽어올 때 복호화하는 JPA Attribute Converter 입니다.
 */
@Converter
@Component
@Slf4j
public class AES256Converter implements AttributeConverter<String, String> {
    private final SecretKeySpec secretKeySpec;
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int IV_SIZE = 16; // 128 bits

    public AES256Converter(@Value("${tikkle.encrypt.secret-key}") String secretKey) {
        if (!StringUtils.hasText(secretKey)) {
            throw new InvalidEncryptionKeyException();
        }
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            throw new InvalidEncryptionKeyException();
        }
        this.secretKeySpec = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (!StringUtils.hasText(attribute)) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[IV_SIZE];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);
            byte[] encrypted = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            // IV를 암호문 앞에 붙여서 저장
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("[AES256Converter] 데이터 암호화 실패", e);
            throw new EncryptionFailedException();
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (!StringUtils.hasText(dbData)) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(dbData);

            // IV와 암호문 분리
            byte[] iv = new byte[IV_SIZE];
            byte[] encrypted = new byte[combined.length - IV_SIZE];
            System.arraycopy(combined, 0, iv, 0, IV_SIZE);
            System.arraycopy(combined, IV_SIZE, encrypted, 0, encrypted.length);

            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[AES256Converter] 데이터 복호화 실패", e);
            throw new EncryptionFailedException();
        }
    }
}