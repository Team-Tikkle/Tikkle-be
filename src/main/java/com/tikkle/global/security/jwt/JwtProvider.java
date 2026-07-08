package com.tikkle.global.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT(JSON Web Token)의 생성, 복호화, 추출, 유효성 검증을 전담하는 프로바이더 컴포넌트입니다.
 */
@Component
public class JwtProvider {
    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    /**
     * 사용자의 이메일을 기반으로 Access Token을 생성합니다.
     *
     * @param email 토큰에 담을 사용자 이메일
     * @return 생성된 JWT Access Token 문자열
     */
    public String createAccessToken(String email) {
        return createToken(email, accessTokenExpiration);
    }

    public String createRefreshToken(String email) {
        return createToken(email, refreshTokenExpiration);
    }

    private String createToken(String email, long expiration) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();
    }

    /**
     * 주어진 JWT 토큰에서 사용자 이메일(Subject)을 추출합니다.
     *
     * @param token JWT 토큰 문자열
     * @return 파싱된 이메일 문자열
     */
    public String getEmail(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * 토큰의 서명 및 만료 여부를 확인하여 유효성 상태를 반환합니다.
     *
     * @param token JWT 토큰 문자열
     * @return 토큰 유효성 검증 결과 (VALID, EXPIRED, INVALID)
     */
    public TokenValidationResult validateTokenWithResult(String token) {
        try {
            parseClaims(token);
            return TokenValidationResult.VALID;
        } catch (ExpiredJwtException e) {
            return TokenValidationResult.EXPIRED;
        } catch (JwtException | IllegalArgumentException e) {
            return TokenValidationResult.INVALID;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * HTTP 요청 헤더에서 'Bearer ' 접두사를 제거하고 순수 JWT 토큰만을 추출합니다.
     *
     * @param request HTTP 요청 객체
     * @return 추출된 토큰 문자열 (없거나 형식이 맞지 않으면 null 반환)
     */
    public String resolveToken(HttpServletRequest request) {
        final String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    public enum TokenValidationResult {
        VALID, EXPIRED, INVALID
    }
}