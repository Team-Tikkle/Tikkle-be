package com.tikkle.payment.filter;

import com.tikkle.global.exception.ErrorCode;
import com.tikkle.global.response.ApiResponse;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 결제 웹훅 요청 본문(body)을 캐싱하여 인터셉터와 컨트롤러에서 여러 번 읽을 수 있게 하는 필터입니다.
 * 서명을 확인하기 전에 본문을 메모리에 올리므로, 상한을 넘는 본문은 읽지 않고 거절합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestBodyCachingFilter extends OncePerRequestFilter {
    private static final String PAYMENT_PATH = "/api/payments";

    // 정상 요청 본문은 1KB 에 못 미친다
    private static final int MAX_BODY_BYTES = 8 * 1024;

    private final JsonMapper objectMapper;

    /**
     * 결제 수신 요청이면 본문을 상한까지만 읽어 캐싱하고, 상한을 넘으면 413 으로 응답합니다.
     * 그 밖의 요청은 그대로 통과시킵니다.
     */
    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull FilterChain filterChain)
            throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !PAYMENT_PATH.equals(request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 길이를 알려 온 요청은 본문을 읽지 않고 판정한다
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            log.warn("[RequestBodyCachingFilter] 본문 크기 상한 초과로 거절 - contentLength: {}", request.getContentLengthLong());
            writePayloadTooLargeResponse(response);
            return;
        }

        // 길이를 알리지 않은 요청도 상한보다 1바이트 더까지만 읽어, 넘는지만 확인한다
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            log.warn("[RequestBodyCachingFilter] 본문 크기 상한 초과로 거절 - 길이 미표기 요청");
            writePayloadTooLargeResponse(response);
            return;
        }

        filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
    }

    private void writePayloadTooLargeResponse(HttpServletResponse response) throws IOException {
        ErrorCode errorCode = ErrorCode.FILE_SIZE_EXCEEDED;
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                objectMapper.writeValueAsString(ApiResponse.error(errorCode))
        );
    }

    public static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request, byte[] cachedBody) {
            super(request);
            this.cachedBody = cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            // 매번 새로운 InputStream을 반환하여 재사용 가능
            return new CachedBodyServletInputStream(this.cachedBody);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        public byte[] getCachedBody() {
            return this.cachedBody;
        }
    }

    private static class CachedBodyServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream inputStream;

        public CachedBodyServletInputStream(byte[] cachedBody) {
            this.inputStream = new ByteArrayInputStream(cachedBody);
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read() {
            return inputStream.read();
        }
    }
}