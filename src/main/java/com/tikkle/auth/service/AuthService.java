package com.tikkle.auth.service;

import com.tikkle.auth.dto.request.ReissueRequest;
import com.tikkle.auth.dto.request.LoginRequest;
import com.tikkle.auth.dto.request.SignupRequest;
import com.tikkle.auth.dto.response.TokenResponse;
import com.tikkle.auth.entity.RefreshToken;
import com.tikkle.auth.exception.InvalidTokenException;
import com.tikkle.auth.exception.RefreshTokenExpiredException;
import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;
import com.tikkle.global.security.jwt.JwtProvider;
import com.tikkle.auth.repository.RefreshTokenRepository;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.UserStatus;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입, 로그인, 로그아웃, 토큰 재발급 등 인증 관련 비즈니스 로직을 처리하는 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsService smsService;

    /**
     * 휴대폰 인증 토큰을 검증하고 새로운 유저를 생성합니다.
     *
     * @param request 회원가입 요청 DTO (이름, 휴대폰 번호, 비밀번호, 인증 토큰)
     * @return 발급된 AccessToken 및 RefreshToken 정보를 담은 TokenResponse
     */
    @Transactional
    public TokenResponse signup(SignupRequest request) {
        // 1. 휴대폰 인증 토큰 검증
        smsService.validateSignupToken(request.getPhoneNumber(), request.getSignupToken());

        // 2. 이미 존재하는 유저인지 확인
        if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            throw new CustomException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }

        // 3. 비밀번호 해싱 및 유저 생성
        User user = User.builder()
                .name(request.getName())
                .phoneNumber(request.getPhoneNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        return issueTokens(user.getPhoneNumber(), true);
    }

    /**
     * 휴대폰 번호와 비밀번호를 확인하여 로그인 처리를 하고 토큰을 발급합니다.
     *
     * @param request 로그인 요청 DTO (휴대폰 번호, 비밀번호)
     * @return 발급된 AccessToken 및 RefreshToken 정보를 담은 TokenResponse
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByPhoneNumberAndStatus(request.getPhoneNumber(), UserStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        return issueTokens(user.getPhoneNumber(), false);
    }

    /**
     * 주어진 휴대폰 번호에 해당하는 사용자의 RefreshToken을 삭제하여 로그아웃 처리합니다.
     *
     * @param phoneNumber 로그아웃할 사용자의 휴대폰 번호
     */
    public void logout(String phoneNumber) {
        log.info("[AuthService] 로그아웃 요청 - phoneNumber: {}", phoneNumber);
        refreshTokenRepository.deleteById(phoneNumber);
        log.info("[AuthService] 로그아웃 처리 완료 - phoneNumber: {}", phoneNumber);
    }

    /**
     * 유효한 RefreshToken을 검증하고, 새로운 토큰 쌍을 발급합니다.
     *
     * @param request 토큰 재발급 요청 DTO (기존 RefreshToken)
     * @return 새로 발급된 AccessToken 및 RefreshToken 정보를 담은 TokenResponse
     */
    public TokenResponse reissue(ReissueRequest request) {
        log.info("[AuthService] 토큰 재발급 요청");
        final JwtProvider.TokenValidationResult result = jwtProvider.validateTokenWithResult(request.refreshToken());

        if (result == JwtProvider.TokenValidationResult.INVALID) {
            throw new InvalidTokenException();
        }
        if (result == JwtProvider.TokenValidationResult.EXPIRED) {
            throw new RefreshTokenExpiredException();
        }

        final String phoneNumber = jwtProvider.getPhoneNumber(request.refreshToken());
        final RefreshToken savedToken = refreshTokenRepository.findById(phoneNumber)
                .orElseThrow(InvalidTokenException::new);

        if (!savedToken.getToken().equals(request.refreshToken())) {
            throw new InvalidTokenException();
        }

        return issueTokens(phoneNumber, false);
    }

    private TokenResponse issueTokens(String phoneNumber, boolean isNewUser) {
        final String newAccessToken = jwtProvider.createAccessToken(phoneNumber);
        final String newRefreshToken = jwtProvider.createRefreshToken(phoneNumber);

        refreshTokenRepository.save(new RefreshToken(
                phoneNumber,
                newRefreshToken,
                jwtProvider.getRefreshTokenExpiration() / 1000
        ));

        return new TokenResponse(newAccessToken, newRefreshToken, isNewUser);
    }
}