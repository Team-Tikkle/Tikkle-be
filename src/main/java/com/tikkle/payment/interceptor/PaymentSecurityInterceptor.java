package com.tikkle.payment.interceptor;

import com.tikkle.payment.exception.ExpiredTimestampException;
import com.tikkle.payment.exception.InvalidSignatureException;
import com.tikkle.payment.filter.RequestBodyCachingFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

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
            throw new InvalidSignatureException();
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            throw new InvalidSignatureException();
        }

        long currentTime = Instant.now().getEpochSecond();
        if (Math.abs(currentTime - timestamp) > TIMESTAMP_EXPIRATION_SEC) {
            throw new ExpiredTimestampException();
        }

        // CachedBodyHttpServletRequest로 캐스팅하여 body 조회
        // instanceof 검사 후 캐스팅하여 body 조회
        if (!(request instanceof RequestBodyCachingFilter.CachedBodyHttpServletRequest cachedRequest)) {
            throw new IllegalStateException("RequestBodyCachingFilter가 적용되지 않았습니다.");
        }

        String payload = new String(cachedRequest.getCachedBody(), StandardCharsets.UTF_8);

        if (!signatureValidator.isValid(payload, timestampStr, signature)) {
            throw new InvalidSignatureException();
        }

        return true;
    }
}