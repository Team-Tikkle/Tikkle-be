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
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OAuthService {
    private final GoogleOAuthClient googleOAuthClient;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public TokenResponse googleLogin(GoogleLoginRequest request) {
        final GoogleUserInfo userInfo = googleOAuthClient.getUserInfo(request.accessToken());

        final User user = userRepository.findByEmailAndStatus(userInfo.email(), UserStatus.ACTIVE)
                .orElseGet(() -> userRepository.save(User.builder()
                        .name(userInfo.name())
                        .email(userInfo.email())
                        .provider(AuthProvider.GOOGLE)
                        .providerId(userInfo.sub())
                        .status(UserStatus.ACTIVE)
                        .build()));

        final String accessToken = jwtProvider.createAccessToken(user.getEmail());
        final String refreshToken = jwtProvider.createRefreshToken(user.getEmail());

        refreshTokenRepository.save(new RefreshToken(
                user.getEmail(),
                refreshToken,
                jwtProvider.getRefreshTokenExpiration() / 1000
        ));

        return new TokenResponse(accessToken, refreshToken);
    }
}
