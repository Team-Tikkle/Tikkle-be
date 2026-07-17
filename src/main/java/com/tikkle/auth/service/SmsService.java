package com.tikkle.auth.service;

import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.service.DefaultMessageService;
import com.tikkle.auth.exception.SmsSendFailedException;
import com.tikkle.auth.exception.SmsSendCooldownException;
import com.tikkle.auth.exception.SmsDailyLimitExceededException;
import com.tikkle.auth.exception.InvalidVerificationCodeException;
import com.tikkle.auth.exception.VerificationAttemptExceededException;
import com.tikkle.auth.exception.InvalidVerificationTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.tikkle.auth.entity.enums.SmsPurpose;

/**
 * 휴대폰 본인인증(SMS 발송 및 검증)을 담당하는 서비스입니다.
 * 모든 상태는 Redis에 보관하며 RDB 트랜잭션과 무관하게 동작합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private static final String SMS_AUTH_KEY_PREFIX = "SMS_AUTH:";
    private static final String SMS_ATTEMPT_KEY_PREFIX = "SMS_ATTEMPT:";
    private static final String SMS_COOLDOWN_KEY_PREFIX = "SMS_COOLDOWN:";
    private static final String SMS_DAILY_KEY_PREFIX = "SMS_DAILY:";
    private static final String SIGNUP_TOKEN_KEY_PREFIX = "SIGNUP_TOKEN:";
    private static final String PASSWORD_RESET_TOKEN_KEY_PREFIX = "PASSWORD_RESET_TOKEN:";

    private static final int CODE_TTL_MINUTES = 3;
    private static final int TOKEN_TTL_MINUTES = 30;
    private static final int SEND_COOLDOWN_SECONDS = 60;
    private static final int DAILY_SEND_LIMIT = 5;
    private static final int MAX_VERIFY_ATTEMPTS = 5;

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

    private String getAttemptKey(String phoneNumber, SmsPurpose purpose) {
        return SMS_ATTEMPT_KEY_PREFIX + purpose.name() + ":" + phoneNumber;
    }

    private String getCooldownKey(String phoneNumber, SmsPurpose purpose) {
        return SMS_COOLDOWN_KEY_PREFIX + purpose.name() + ":" + phoneNumber;
    }

    private String getDailyKey(String phoneNumber, SmsPurpose purpose) {
        return SMS_DAILY_KEY_PREFIX + purpose.name() + ":" + phoneNumber;
    }

    private String getTokenKey(String phoneNumber, SmsPurpose purpose) {
        if (purpose == SmsPurpose.SIGNUP) {
            return SIGNUP_TOKEN_KEY_PREFIX + phoneNumber;
        }
        return PASSWORD_RESET_TOKEN_KEY_PREFIX + phoneNumber;
    }

    /**
     * 발송 쿼터(재발송 쿨다운 60초 + 24시간 내 5회 한도)를 검사하고 소모합니다.
     * 실제 발송 여부와 무관하게 호출되어야 가입 여부에 따라 응답이 달라지는 사용자 열거를 막을 수 있습니다.
     *
     * @param phoneNumber 대상 휴대폰 번호
     * @param purpose     인증 목적 (SIGNUP, PASSWORD_RESET)
     */
    public void consumeSendQuota(String phoneNumber, SmsPurpose purpose) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(getCooldownKey(phoneNumber, purpose), "1", SEND_COOLDOWN_SECONDS, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(acquired)) {
            log.warn("[SmsService] 인증번호 재발송 쿨다운 위반 - phoneNumber: {}, purpose: {}", phoneNumber, purpose);
            throw new SmsSendCooldownException();
        }

        String dailyKey = getDailyKey(phoneNumber, purpose);
        Long dailyCount = redisTemplate.opsForValue().increment(dailyKey);

        // 첫 발송 시에만 TTL을 부여해 24시간 롤링 윈도우로 동작시킨다
        if (dailyCount != null && dailyCount == 1L) {
            redisTemplate.expire(dailyKey, 24, TimeUnit.HOURS);
        }

        if (dailyCount != null && dailyCount > DAILY_SEND_LIMIT) {
            log.warn("[SmsService] 일일 인증번호 발송 한도 초과 - phoneNumber: {}, purpose: {}, count: {}", phoneNumber, purpose, dailyCount);
            throw new SmsDailyLimitExceededException();
        }
    }

    /**
     * 사용자의 휴대폰 번호로 6자리 인증번호를 발송합니다. 발송 쿼터를 소모한 뒤 실제로 발송합니다.
     *
     * @param phoneNumber 수신할 휴대폰 번호
     * @param purpose     인증 목적 (SIGNUP, PASSWORD_RESET)
     */
    public void sendVerificationCode(String phoneNumber, SmsPurpose purpose) {
        consumeSendQuota(phoneNumber, purpose);

        String verificationCode = generateRandomCode();

        Message message = new Message();
        message.setFrom(senderNumber);
        message.setTo(phoneNumber);
        message.setText("[Tikkle] 인증번호는 [" + verificationCode + "] 입니다.");

        try {
            messageService.sendOne(new SingleMessageSendingRequest(message));

            redisTemplate.opsForValue().set(
                getSmsAuthKey(phoneNumber, purpose),
                verificationCode,
                CODE_TTL_MINUTES,
                TimeUnit.MINUTES
            );
            // 새 인증번호를 발급했으므로 이전 인증번호의 실패 카운터를 초기화한다
            redisTemplate.delete(getAttemptKey(phoneNumber, purpose));

            log.info("[SmsService] 인증번호 발송 완료 - phoneNumber: {}, purpose: {}", phoneNumber, purpose);
        } catch (Exception e) {
            log.error("[SmsService] 인증번호 발송 실패 - phoneNumber: {}", phoneNumber, e);
            throw new SmsSendFailedException();
        }
    }

    /**
     * 회원가입용 인증번호를 발송합니다.
     */
    public void sendVerificationCode(String phoneNumber) {
        sendVerificationCode(phoneNumber, SmsPurpose.SIGNUP);
    }

    /**
     * 회원가입용 인증번호를 검증하고 임시 토큰을 반환합니다.
     */
    public String verifyCodeAndGetToken(String phoneNumber, String code) {
        return verifyCodeAndGetToken(phoneNumber, code, SmsPurpose.SIGNUP);
    }

    /**
     * 발송된 인증번호를 검증하고, 임시 토큰을 반환합니다.
     * 인증번호 오입력이 {@value #MAX_VERIFY_ATTEMPTS}회 누적되면 해당 인증번호를 폐기하여 무차별 대입을 차단합니다.
     *
     * @param phoneNumber 인증 요청 휴대폰 번호
     * @param code 사용자 입력 인증번호
     * @param purpose 인증 목적
     * @return 검증 성공 시 발급되는 토큰
     */
    public String verifyCodeAndGetToken(String phoneNumber, String code, SmsPurpose purpose) {
        String codeKey = getSmsAuthKey(phoneNumber, purpose);
        String storedCode = redisTemplate.opsForValue().get(codeKey);

        if (storedCode == null) {
            log.warn("[SmsService] 인증번호 미발급 또는 만료 - phoneNumber: {}, purpose: {}", phoneNumber, purpose);
            throw new InvalidVerificationCodeException();
        }

        if (!storedCode.equals(code)) {
            handleFailedAttempt(phoneNumber, purpose, codeKey);
        }

        // 검증 성공 시 인증번호와 실패 카운터를 함께 정리
        redisTemplate.delete(codeKey);
        redisTemplate.delete(getAttemptKey(phoneNumber, purpose));

        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
            getTokenKey(phoneNumber, purpose),
            token,
            TOKEN_TTL_MINUTES,
            TimeUnit.MINUTES
        );
        log.info("[SmsService] 인증번호 검증 성공 - phoneNumber: {}, purpose: {}", phoneNumber, purpose);
        return token;
    }

    /**
     * 인증번호 오입력을 카운트하고 항상 예외를 던진다.
     * 한도 초과 시 인증번호 자체를 폐기하여 무차별 대입을 차단한다.
     */
    private void handleFailedAttempt(String phoneNumber, SmsPurpose purpose, String codeKey) {
        String attemptKey = getAttemptKey(phoneNumber, purpose);
        Long attempts = redisTemplate.opsForValue().increment(attemptKey);

        if (attempts != null && attempts == 1L) {
            // 인증번호와 동일한 수명을 부여해 카운터가 영구히 남지 않게 한다
            redisTemplate.expire(attemptKey, CODE_TTL_MINUTES, TimeUnit.MINUTES);
        }

        if (attempts != null && attempts >= MAX_VERIFY_ATTEMPTS) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(attemptKey);
            log.warn("[SmsService] 인증번호 입력 한도 초과로 인증번호 폐기 - phoneNumber: {}, purpose: {}", phoneNumber, purpose);
            throw new VerificationAttemptExceededException();
        }

        log.warn("[SmsService] 인증번호 검증 실패 - phoneNumber: {}, purpose: {}, attempts: {}/{}", phoneNumber, purpose, attempts, MAX_VERIFY_ATTEMPTS);
        throw new InvalidVerificationCodeException();
    }

    /**
     * 회원가입 시 발급받은 signupToken이 유효한지 검증합니다. (토큰을 소모하지는 않습니다)
     *
     * @param phoneNumber 회원가입 요청 휴대폰 번호
     * @param token 검증할 signupToken
     */
    public void validateSignupToken(String phoneNumber, String token) {
        validateToken(phoneNumber, token, SmsPurpose.SIGNUP);
    }

    /**
     * 비밀번호 재설정 시 발급받은 resetToken이 유효한지 검증합니다. (토큰을 소모하지는 않습니다)
     */
    public void validatePasswordResetToken(String phoneNumber, String token) {
        validateToken(phoneNumber, token, SmsPurpose.PASSWORD_RESET);
    }

    /**
     * 임시 토큰을 소모(삭제)합니다. 후속 DB 트랜잭션이 커밋된 뒤에 호출해야
     * 트랜잭션 실패 시 사용자가 SMS 인증을 다시 하지 않아도 됩니다.
     *
     * @param phoneNumber 대상 휴대폰 번호
     * @param purpose 인증 목적
     */
    public void consumeToken(String phoneNumber, SmsPurpose purpose) {
        redisTemplate.delete(getTokenKey(phoneNumber, purpose));
        log.info("[SmsService] 임시 토큰 소모 완료 - phoneNumber: {}, purpose: {}", phoneNumber, purpose);
    }

    private void validateToken(String phoneNumber, String token, SmsPurpose purpose) {
        String storedToken = redisTemplate.opsForValue().get(getTokenKey(phoneNumber, purpose));
        if (storedToken == null || !storedToken.equals(token)) {
            log.warn("[SmsService] 임시 토큰 검증 실패 - phoneNumber: {}, purpose: {}", phoneNumber, purpose);
            throw new InvalidVerificationTokenException();
        }
        log.info("[SmsService] 임시 토큰 검증 성공 - phoneNumber: {}, purpose: {}", phoneNumber, purpose);
    }

    private String generateRandomCode() {
        int code = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(code);
    }
}
