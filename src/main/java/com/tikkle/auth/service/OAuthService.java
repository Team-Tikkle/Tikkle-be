package com.tikkle.auth.service;

import com.tikkle.auth.client.GoogleOAuthClient;
import com.tikkle.auth.client.GoogleUserInfo;
import com.tikkle.auth.dto.request.GoogleLoginRequest;
import com.tikkle.auth.dto.response.TokenResponse;
import com.tikkle.auth.entity.RefreshToken;
import com.tikkle.auth.jwt.JwtProvider;
import com.tikkle.auth.repository.RefreshTokenRepository;
import com.tikkle.user.entity.AuthProvider;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.UserStatus;
import com.tikkle.user.exception.WithdrawnUserException;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OAuthService {
    private final GoogleOAuthClient googleOAuthClient;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public TokenResponse googleLogin(GoogleLoginRequest request) {
        // 외부 HTTP 호출은 트랜잭션 밖에서 수행
        final GoogleUserInfo userInfo = googleOAuthClient.getUserInfo(request.accessToken());

        final User user = findOrCreateUser(userInfo);

        final String accessToken = jwtProvider.createAccessToken(user.getEmail());
        final String refreshToken = jwtProvider.createRefreshToken(user.getEmail());

        refreshTokenRepository.save(new RefreshToken(
                user.getEmail(),
                refreshToken,
                jwtProvider.getRefreshTokenExpiration() / 1000
        ));

        return new TokenResponse(accessToken, refreshToken);
    }

    private User findOrCreateUser(GoogleUserInfo userInfo) {
        return userRepository.findByEmail(userInfo.email())
                .map(existing -> {
                    // 탈퇴한 계정은 보관 기간 동안 재로그인 차단 (보관 기간 경과 후 데이터 삭제되면 신규 가입)
                    if (existing.getStatus() == UserStatus.WITHDRAWN) {
                        throw new WithdrawnUserException();
                    }
                    return existing;
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .name(userInfo.name())
                        .email(userInfo.email())
                        .provider(AuthProvider.GOOGLE)
                        .providerId(userInfo.sub())
                        .status(UserStatus.ACTIVE)
                        .build()));
    }
}