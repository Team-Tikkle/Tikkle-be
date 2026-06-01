package com.tikkle.auth.security;

import tools.jackson.databind.json.JsonMapper;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    private final JsonMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("[AccessDeniedHandler] 권한 없는 접근 - URI: {}", request.getRequestURI());
        response.setStatus(ErrorCode.ACCESS_DENIED.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                objectMapper.writeValueAsString(
                        ApiResponse.error(ErrorCode.ACCESS_DENIED.getCode(), ErrorCode.ACCESS_DENIED.getMessage())
                )
        );
    }
}