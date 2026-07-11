package com.tikkle.global.security.jwt;

import com.tikkle.global.exception.ErrorCode;
import com.tikkle.global.response.ApiResponse;
import com.tikkle.global.security.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 요청이 들어올 때마다 헤더에서 JWT 토큰을 추출하고 유효성을 검증하여,
 * 유효한 토큰일 경우 SecurityContext에 인증 정보를 등록하는 필터입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;
    private final JsonMapper objectMapper;

    /**
     * 필터 핵심 로직을 수행합니다. 토큰을 파싱 및 검증하고, 결과에 따라 인증하거나 에러 응답을 반환합니다.
     *
     * @param request  HTTP 요청 객체
     * @param response HTTP 응답 객체
     * @param filterChain 필터 체인
     * @throws ServletException 서블릿 예외
     * @throws IOException 입출력 예외
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        final String token = jwtProvider.resolveToken(request);

        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        final JwtProvider.TokenValidationResult result = jwtProvider.validateTokenWithResult(token);

        switch (result) {
            case VALID -> {
                try {
                    final String phoneNumber = jwtProvider.getPhoneNumber(token);
                    final UserDetails userDetails = userDetailsService.loadUserByUsername(phoneNumber);
                    final UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    filterChain.doFilter(request, response);
                } catch (Exception e) {
                    log.error("[JwtAuthenticationFilter] 인증 처리 중 예외 발생 - URI: {}", request.getRequestURI(), e);
                    SecurityContextHolder.clearContext();
                    writeErrorResponse(response, ErrorCode.INVALID_TOKEN);
                }
            }
            case EXPIRED -> {
                log.warn("[JwtAuthenticationFilter] 만료된 토큰 - URI: {}", request.getRequestURI());
                writeErrorResponse(response, ErrorCode.EXPIRED_TOKEN);
            }
            case INVALID -> {
                log.warn("[JwtAuthenticationFilter] 유효하지 않은 토큰 - URI: {}", request.getRequestURI());
                writeErrorResponse(response, ErrorCode.INVALID_TOKEN);
            }
        }
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                objectMapper.writeValueAsString(
                        ApiResponse.error(errorCode.getCode(), errorCode.getMessage())
                )
        );
    }
}