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

    /**
     * 사용자의 휴대폰 번호로 6자리 인증번호를 발송합니다.
     *
     * @param phoneNumber 수신할 휴대폰 번호
     */
    @Transactional
    public void sendVerificationCode(String phoneNumber) {
        String verificationCode = generateRandomCode();
        
        Message message = new Message();
        message.setFrom(senderNumber);
        message.setTo(phoneNumber);
        message.setText("[Tikkle] 인증번호는 [" + verificationCode + "] 입니다.");

        try {
            messageService.sendOne(new SingleMessageSendingRequest(message));
            
            // Save to Redis (TTL: 3 minutes)
            redisTemplate.opsForValue().set(
                SMS_AUTH_KEY_PREFIX + phoneNumber, 
                verificationCode, 
                3, 
                TimeUnit.MINUTES
            );
            log.info("[SmsService] 인증번호 발송 완료 - phoneNumber: {}", phoneNumber);
        } catch (Exception e) {
            log.error("[SmsService] 인증번호 발송 실패 - phoneNumber: {}", phoneNumber, e);
            throw new SmsSendFailedException();
        }
    }

    /**
     * 발송된 인증번호를 검증하고, 회원가입용 임시 토큰을 반환합니다.
     *
     * @param phoneNumber 인증 요청 휴대폰 번호
     * @param code 사용자 입력 인증번호
     * @return 검증 성공 시 발급되는 signupToken
     */
    @Transactional
    public String verifyCodeAndGetToken(String phoneNumber, String code) {
        String storedCode = redisTemplate.opsForValue().get(SMS_AUTH_KEY_PREFIX + phoneNumber);
        
        if (storedCode != null && storedCode.equals(code)) {
            // 검증 성공 시 재사용 방지용 삭제
            redisTemplate.delete(SMS_AUTH_KEY_PREFIX + phoneNumber);
            
            // 회원가입 시 인증 성공 여부를 증명할 임시 토큰 발급
            String signupToken = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(
                SIGNUP_TOKEN_KEY_PREFIX + phoneNumber,
                signupToken,
                30,
                TimeUnit.MINUTES
            );
            log.info("[SmsService] 인증번호 검증 성공 - phoneNumber: {}", phoneNumber);
            return signupToken;
        }
        
        log.warn("[SmsService] 인증번호 검증 실패 - phoneNumber: {}", phoneNumber);
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
        String storedToken = redisTemplate.opsForValue().get(SIGNUP_TOKEN_KEY_PREFIX + phoneNumber);
        if (storedToken == null || !storedToken.equals(token)) {
            log.warn("[SmsService] 회원가입 토큰 검증 실패 - phoneNumber: {}", phoneNumber);
            throw new ExpiredSignupTokenException();
        }
        // 검증 성공 시 삭제 (1회용)
        redisTemplate.delete(SIGNUP_TOKEN_KEY_PREFIX + phoneNumber);
        log.info("[SmsService] 회원가입 토큰 검증 성공 - phoneNumber: {}", phoneNumber);
    }

    private String generateRandomCode() {
        int code = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(code);
    }
}