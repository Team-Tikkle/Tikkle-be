package com.tikkle.auth.service;

import com.tikkle.auth.dto.response.TokenResponse;
import com.tikkle.auth.entity.RefreshToken;
import com.tikkle.auth.repository.RefreshTokenRepository;
import com.tikkle.global.security.jwt.JwtProvider;
import com.tikkle.user.entity.User;

import com.tikkle.user.exception.UserNotFoundException;
import com.tikkle.user.repository.UserRepository;
import com.tikkle.investment.entity.InvestmentProfile;
import com.tikkle.investment.repository.InvestmentProfileRepository;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.repository.LinkedAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 개발 환경(local)에서 프론트엔드 개발 시 편리하게 인증 토큰을 발급받기 위한 테스트 서비스입니다.
 */
@Service
@Profile("local")
@RequiredArgsConstructor
public class TestTokenService {
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final InvestmentProfileRepository investmentProfileRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public TokenResponse generateTestToken(String phoneNumber) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(UserNotFoundException::new);
        return issueToken(user.getId(), false);
    }

    public TokenResponse generateTestSignupAndToken(String phoneNumber, String name) {
        final Optional<User> existingUser = userRepository.findByPhoneNumber(phoneNumber);
        final boolean isNewUser = existingUser.isEmpty();
        final User user = existingUser.orElseGet(() -> {
            User newUser = userRepository.save(User.builder()
                    .phoneNumber(phoneNumber)
                    // 비밀번호 정책(영문+숫자+특수문자 8~20자)을 만족해야 이 계정으로 로그인 API를 호출할 수 있다
                    .password(passwordEncoder.encode("testpassword1!"))
                    .name(name)
                    .build());
            investmentProfileRepository.save(InvestmentProfile.builder().user(newUser).build());
            linkedAccountRepository.save(LinkedAccount.builder().user(newUser).build());
            return newUser;
        });
        return issueToken(user.getId(), isNewUser);
    }

    private TokenResponse issueToken(Long userId, boolean isNewUser) {
        final String accessToken = jwtProvider.createAccessToken(userId);
        final String refreshToken = jwtProvider.createRefreshToken(userId);
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .token(refreshToken)
                .expiration(jwtProvider.getRefreshTokenExpiration() / 1000)
                .build());
        return new TokenResponse(accessToken, refreshToken, isNewUser);
    }
}