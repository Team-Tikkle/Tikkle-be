package com.tikkle.global.security;

import com.tikkle.global.exception.ErrorCode;
import com.tikkle.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증된 사용자지만, 접근 권한이 없는(Role 불일치 등) 리소스에 접근하려 할 때 발생하는 예외를 가로채어
 * 403 Forbidden 에러 응답을 JSON 형태로 반환하는 핸들러입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    private final JsonMapper objectMapper;

    /**
     * 인가 예외 발생 시 호출되며, Custom 공통 규격(ApiResponse)에 맞춰 에러를 출력합니다.
     *
     * @param request HTTP 요청 객체
     * @param response HTTP 응답 객체
     * @param accessDeniedException 발생한 인가(권한) 예외
     * @throws IOException 입출력 예외
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("[AccessDeniedHandler] 권한 없는 접근 - URI: {}", request.getRequestURI());
        response.setStatus(ErrorCode.ACCESS_DENIED.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                objectMapper.writeValueAsString(ApiResponse.error(ErrorCode.ACCESS_DENIED))
        );
    }
}