package com.tikkle.auth.service;

import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.service.DefaultMessageService;
import com.tikkle.auth.exception.SmsSendFailedException;
import com.tikkle.auth.exception.InvalidVerificationCodeException;
import com.tikkle.auth.exception.ExpiredSignupTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.tikkle.auth.entity.enums.SmsPurpose;

/**
 * 휴대폰 본인인증(SMS 발송 및 검증)을 담당하는 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private static final String SMS_AUTH_KEY_PREFIX = "SMS_AUTH:";
    private static final String SIGNUP_TOKEN_KEY_PREFIX = "SIGNUP_TOKEN:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private DefaultMessageService messageService;

    @Value("${coolsms.api.key}")
    private String apiKey;

    @Value("${coolsms.api.secret}")
    private String apiSecret;

    @Value("${coolsms.sender-number}")
    private String senderNumber;

    private final StringRedisTemplate redisTemplate;

    @PostConstruct
    public void init() {
        this.messageService = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, "https://api.coolsms.co.kr");
    }

    private String getSmsAuthKey(String phoneNumber, SmsPurpose purpose) {
        return SMS_AUTH_KEY_PREFIX + purpose.name() + ":" + phoneNumber;
    }

    private String getTokenKey(String phoneNumber, SmsPurpose purpose) {
        if (purpose == SmsPurpose.SIGNUP) {
            return SIGNUP_TOKEN_KEY_PREFIX + phoneNumber;
        }
        return "PASSWORD_RESET_TOKEN:" + phoneNumber;
    }

    /**
     * 사용자의 휴대폰 번호로 6자리 인증번호를 발송합니다.
     *
     * @param phoneNumber 수신할 휴대폰 번호
     * @param purpose     인증 목적 (SIGNUP, PASSWORD_RESET)
     */
    @Transactional
    public void sendVerificationCode(String phoneNumber, SmsPurpose purpose) {
        String verificationCode = generateRandomCode();
        
        Message message = new Message();
        message.setFrom(senderNumber);
        message.setTo(phoneNumber);
        message.setText("[Tikkle] 인증번호는 [" + verificationCode + "] 입니다.");

        try {
            messageService.sendOne(new SingleMessageSendingRequest(message));
            
            // Save to Redis (TTL: 3 minutes)
            redisTemplate.opsForValue().set(
                getSmsAuthKey(phoneNumber, purpose), 
                verificationCode, 
                3, 
                TimeUnit.MINUTES
            );
            log.info("[SmsService] 인증번호 발송 완료 - phoneNumber: {}, purpose: {}", phoneNumber, purpose);
        } catch (Exception e) {
            log.error("[SmsService] 인증번호 발송 실패 - phoneNumber: {}", phoneNumber, e);
            throw new SmsSendFailedException();
        }
    }

    /**
     * 사용자의 휴대폰 번호로 6자리 인증번호를 발송합니다. (하위 호환성용 - SIGNUP)
     */
    @Transactional
    public void sendVerificationCode(String phoneNumber) {
        sendVerificationCode(phoneNumber, SmsPurpose.SIGNUP);
    }

    /**
     * 발송된 인증번호를 검증하고, 임시 토큰을 반환합니다. (하위 호환성용 - SIGNUP)
     */
    @Transactional
    public String verifyCodeAndGetToken(String phoneNumber, String code) {
        return verifyCodeAndGetToken(phoneNumber, code, SmsPurpose.SIGNUP);
    }

    /**
     * 발송된 인증번호를 검증하고, 임시 토큰을 반환합니다.
     *
     * @param phoneNumber 인증 요청 휴대폰 번호
     * @param code 사용자 입력 인증번호
     * @param purpose 인증 목적
     * @return 검증 성공 시 발급되는 토큰
     */
    @Transactional
    public String verifyCodeAndGetToken(String phoneNumber, String code, SmsPurpose purpose) {
        String key = getSmsAuthKey(phoneNumber, purpose);
        String storedCode = redisTemplate.opsForValue().get(key);
        
        if (storedCode != null && storedCode.equals(code)) {
            // 검증 성공 시 재사용 방지용 삭제
            redisTemplate.delete(key);
            
            // 인증 성공 여부를 증명할 임시 토큰 발급
            String token = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(
                getTokenKey(phoneNumber, purpose),
                token,
                30,
                TimeUnit.MINUTES
            );
            log.info("[SmsService] 인증번호 검증 성공 - phoneNumber: {}, purpose: {}", phoneNumber, purpose);
            return token;
        }
        
        log.warn("[SmsService] 인증번호 검증 실패 - phoneNumber: {}, purpose: {}", phoneNumber, purpose);
        throw new InvalidVerificationCodeException();
    }

    /**
     * 회원가입 시 발급받은 signupToken이 유효한지 검증합니다.
     *
     * @param phoneNumber 회원가입 요청 휴대폰 번호
     * @param token 검증할 signupToken
     */
    @Transactional
    public void validateSignupToken(String phoneNumber, String token) {
        validateToken(phoneNumber, token, SmsPurpose.SIGNUP);
    }

    /**
     * 비밀번호 재설정 시 발급받은 resetToken이 유효한지 검증합니다.
     */
    @Transactional
    public void validatePasswordResetToken(String phoneNumber, String token) {
        validateToken(phoneNumber, token, SmsPurpose.PASSWORD_RESET);
    }

    private void validateToken(String phoneNumber, String token, SmsPurpose purpose) {
        String key = getTokenKey(phoneNumber, purpose);
        String storedToken = redisTemplate.opsForValue().get(key);
        if (storedToken == null || !storedToken.equals(token)) {
            log.warn("[SmsService] 임시 토큰 검증 실패 - phoneNumber: {}, purpose: {}", phoneNumber, purpose);
            throw new ExpiredSignupTokenException();
        }
        // 검증 성공 시 삭제 (1회용)
        redisTemplate.delete(key);
        log.info("[SmsService] 임시 토큰 검증 성공 - phoneNumber: {}, purpose: {}", phoneNumber, purpose);
    }

    private String generateRandomCode() {
        int code = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(code);
    }
}