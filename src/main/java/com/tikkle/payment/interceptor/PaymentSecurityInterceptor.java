package com.tikkle.payment.interceptor;

import com.tikkle.payment.exception.ExpiredTimestampException;
import com.tikkle.payment.exception.InvalidSignatureException;
import com.tikkle.payment.filter.RequestBodyCachingFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.tikkle.payment.exception.PaymentFilterConfigurationException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import lombok.extern.slf4j.Slf4j;

/**
 * 결제 스크래핑 요청에 대한 서명 검증을 수행하는 인터셉터입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSecurityInterceptor implements HandlerInterceptor {
    private final SignatureValidator signatureValidator;
    private static final long TIMESTAMP_EXPIRATION_SEC = 300; // 5분

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        // GET 등 다른 HTTP 메서드는 서명 검증을 생략 (웹훅 POST 요청만 검증)
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String signature = request.getHeader("X-Tikkle-Signature");
        String timestampStr = request.getHeader("X-Tikkle-Timestamp");

        if (signature == null || timestampStr == null) {
            log.warn("[PaymentSecurityInterceptor] 서명 또는 타임스탬프 헤더 누락");
            throw new InvalidSignatureException();
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            log.warn("[PaymentSecurityInterceptor] 타임스탬프 파싱 실패 - timestamp: {}", timestampStr);
            throw new InvalidSignatureException();
        }

        long currentTime = Instant.now().getEpochSecond();
        if (Math.abs(currentTime - timestamp) > TIMESTAMP_EXPIRATION_SEC) {
            log.warn("[PaymentSecurityInterceptor] 타임스탬프 5분 만료 - requestTime: {}, currentTime: {}", timestamp, currentTime);
            throw new ExpiredTimestampException();
        }

        // CachedBodyHttpServletRequest로 캐스팅하여 body 조회
        // instanceof 검사 후 캐스팅하여 body 조회
        if (!(request instanceof RequestBodyCachingFilter.CachedBodyHttpServletRequest cachedRequest)) {
            log.warn("[PaymentSecurityInterceptor] RequestBodyCachingFilter 캐스팅 실패");
            throw new PaymentFilterConfigurationException();
        }

        String payload = new String(cachedRequest.getCachedBody(), StandardCharsets.UTF_8);

        if (!signatureValidator.isValid(payload, timestampStr, signature)) {
            log.warn("[PaymentSecurityInterceptor] 서명 검증 실패");
            throw new InvalidSignatureException();
        }

        return true;
    }
}