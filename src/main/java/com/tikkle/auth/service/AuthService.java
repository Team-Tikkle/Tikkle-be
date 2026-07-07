package com.tikkle.auth.service;

import com.tikkle.auth.dto.request.ReissueRequest;
import com.tikkle.auth.dto.response.TokenResponse;
import com.tikkle.auth.entity.RefreshToken;
import com.tikkle.auth.exception.InvalidTokenException;
import com.tikkle.auth.exception.RefreshTokenExpiredException;
import com.tikkle.global.security.jwt.JwtProvider;
import com.tikkle.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 로그아웃 및 토큰 재발급 등 인증 관련 비즈니스 로직을 처리하는 서비스 클래스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * 사용자의 리프레시 토큰을 레디스에서 삭제하여 로그아웃 처리합니다.
     *
     * @param email 사용자 이메일
     */
    public void logout(String email) {
        log.info("[AuthService] 로그아웃 요청 - email: {}", email);
        refreshTokenRepository.deleteById(email);
        log.info("[AuthService] 로그아웃 처리 완료 - email: {}", email);
    }

    /**
     * 리프레시 토큰을 검증하고, 유효한 경우 새로운 액세스 토큰과 리프레시 토큰을 발급합니다.
     *
     * @param request 토큰 재발급 요청 (리프레시 토큰 포함)
     * @return 새로운 토큰 응답
     */
    public TokenResponse reissue(ReissueRequest request) {
        log.info("[AuthService] 토큰 재발급 요청");
        final JwtProvider.TokenValidationResult result = jwtProvider.validateTokenWithResult(request.refreshToken());

        if (result == JwtProvider.TokenValidationResult.INVALID) {
            log.warn("[AuthService] 유효하지 않은 리프레시 토큰으로 재발급 시도");
            throw new InvalidTokenException();
        }
        if (result == JwtProvider.TokenValidationResult.EXPIRED) {
            log.warn("[AuthService] 만료된 리프레시 토큰으로 재발급 시도");
            throw new RefreshTokenExpiredException();
        }

        final String email = jwtProvider.getEmail(request.refreshToken());
        final RefreshToken savedToken = refreshTokenRepository.findById(email)
                .orElseThrow(() -> {
                    log.warn("[AuthService] Redis에 저장된 리프레시 토큰이 없음 - email: {}", email);
                    return new InvalidTokenException();
                });

        if (!savedToken.getToken().equals(request.refreshToken())) {
            log.warn("[AuthService] 전달된 토큰과 Redis에 저장된 토큰 불일치 - email: {}", email);
            throw new InvalidTokenException();
        }

        final String newAccessToken = jwtProvider.createAccessToken(email);
        final String newRefreshToken = jwtProvider.createRefreshToken(email);

        refreshTokenRepository.save(new RefreshToken(
                email,
                newRefreshToken,
                jwtProvider.getRefreshTokenExpiration() / 1000
        ));

        return new TokenResponse(newAccessToken, newRefreshToken, false);
    }
}