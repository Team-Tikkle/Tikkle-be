package com.tikkle.global.security;

import com.tikkle.global.exception.ErrorCode;
import com.tikkle.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증되지 않은 사용자(미로그인 등)가 보호된 리소스에 접근하려 할 때 발생하는 예외를 가로채어
 * 401 Unauthorized 에러 응답을 JSON 형태로 반환하는 핸들러입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final JsonMapper objectMapper;

    /**
     * 인증 예외 발생 시 호출되며, Custom 공통 규격(ApiResponse)에 맞춰 에러를 출력합니다.
     *
     * @param request HTTP 요청 객체
     * @param response HTTP 응답 객체
     * @param authException 발생한 인증 예외
     * @throws IOException 입출력 예외
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("[AuthenticationEntryPoint] 인증되지 않은 접근 - URI: {}", request.getRequestURI());
        response.setStatus(ErrorCode.UNAUTHORIZED.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                objectMapper.writeValueAsString(
                        ApiResponse.error(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage())
                )
        );
    }
}