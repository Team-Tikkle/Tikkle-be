package com.tikkle.auth.service;

import com.tikkle.auth.client.GoogleOAuthClient;
import com.tikkle.auth.client.GoogleUserInfo;
import com.tikkle.auth.dto.request.GoogleLoginRequest;
import com.tikkle.auth.dto.response.TokenResponse;
import com.tikkle.auth.entity.RefreshToken;
import com.tikkle.global.security.jwt.JwtProvider;
import com.tikkle.auth.repository.RefreshTokenRepository;
import com.tikkle.user.entity.enums.AuthProvider;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.UserStatus;
import com.tikkle.user.exception.WithdrawnUserException;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 구글 소셜 로그인 등 OAuth 기반 인증 로직을 처리하는 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {
    private final GoogleOAuthClient googleOAuthClient;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * 프론트엔드로부터 구글 액세스 토큰을 받아 구글 서버에서 유저 정보를 조회하고,
     * 자사 서비스의 JWT 액세스/리프레시 토큰을 발급하여 반환합니다.
     *
     * @param request 구글 로그인 요청 객체
     * @return 자체 JWT 토큰 응답
     */
    public TokenResponse googleLogin(GoogleLoginRequest request) {
        log.info("[OAuthService] 구글 소셜 로그인 처리 시작");
        
        // 외부 HTTP 호출은 트랜잭션 밖에서 수행
        final GoogleUserInfo userInfo = googleOAuthClient.getUserInfo(request.accessToken());
        log.info("[OAuthService] 구글 유저 정보 획득 성공 - email: {}", userInfo.email());

        final LoginUser loginUser = resolveUser(userInfo);
        final User user = loginUser.user();

        final String accessToken = jwtProvider.createAccessToken(user.getEmail());
        final String refreshToken = jwtProvider.createRefreshToken(user.getEmail());

        refreshTokenRepository.save(new RefreshToken(
                user.getEmail(),
                refreshToken,
                jwtProvider.getRefreshTokenExpiration() / 1000
        ));

        return new TokenResponse(accessToken, refreshToken, loginUser.isNewUser());
    }

    private LoginUser resolveUser(GoogleUserInfo userInfo) {
        final Optional<User> existingUser = userRepository.findByEmail(userInfo.email());
        // 탈퇴한 계정은 보관 기간 동안 재로그인 차단 (보관 기간 경과 후 데이터 삭제되면 신규 가입)
        existingUser.ifPresent(user -> {
            if (user.getStatus() == UserStatus.WITHDRAWN) {
                log.warn("[OAuthService] 탈퇴한 사용자의 로그인 시도 차단 - email: {}", userInfo.email());
                throw new WithdrawnUserException();
            }
        });
        if (existingUser.isPresent()) {
            log.info("[OAuthService] 기존 회원 로그인 - email: {}", userInfo.email());
            return new LoginUser(existingUser.get(), false);
        }

        // 신규 가입. 동시 요청이 같은 이메일을 먼저 저장하면 UNIQUE 충돌이 나므로 재조회로 기존 유저를 회수한다.
        try {
            log.info("[OAuthService] 신규 회원 가입 처리 - email: {}", userInfo.email());
            return new LoginUser(userRepository.save(User.builder()
                    .name(userInfo.name())
                    .email(userInfo.email())
                    .provider(AuthProvider.GOOGLE)
                    .providerId(userInfo.sub())
                    .status(UserStatus.ACTIVE)
                    .build()), true);
        } catch (DataIntegrityViolationException e) {
            log.warn("[OAuthService] 신규 가입 중 동시성 충돌 발생, 기존 유저 조회로 Fallback - email: {}", userInfo.email());
            final User user = userRepository.findByEmail(userInfo.email())
                    .orElseThrow(() -> e);
            return new LoginUser(user, false);
        }
    }

    private record LoginUser(User user, boolean isNewUser) {}
}