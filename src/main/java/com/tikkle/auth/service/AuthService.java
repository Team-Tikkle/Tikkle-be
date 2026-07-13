package com.tikkle.auth.service;

import com.tikkle.auth.dto.request.ReissueRequest;
import com.tikkle.auth.dto.request.LoginRequest;
import com.tikkle.auth.dto.request.SignupRequest;
import com.tikkle.auth.dto.response.TokenResponse;
import com.tikkle.auth.entity.RefreshToken;
import com.tikkle.auth.exception.InvalidTokenException;
import com.tikkle.auth.exception.RefreshTokenExpiredException;
import com.tikkle.auth.exception.PhoneAlreadyRegisteredException;
import com.tikkle.auth.exception.InvalidPasswordException;
import com.tikkle.user.exception.UserNotFoundException;
import com.tikkle.global.security.jwt.JwtProvider;
import com.tikkle.auth.repository.RefreshTokenRepository;
import com.tikkle.investment.entity.InvestmentProfile;
import com.tikkle.investment.repository.InvestmentProfileRepository;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.UserStatus;
import com.tikkle.user.repository.LinkedAccountRepository;
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
    private final LinkedAccountRepository linkedAccountRepository;
    private final InvestmentProfileRepository investmentProfileRepository;
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
        smsService.validateSignupToken(request.phoneNumber(), request.signupToken());

        // 2. 이미 존재하는 유저인지 확인
        if (userRepository.findByPhoneNumber(request.phoneNumber()).isPresent()) {
            throw new PhoneAlreadyRegisteredException();
        }

        // 3. 비밀번호 해싱 및 유저 생성
        User user = User.builder()
                .name(request.name())
                .phoneNumber(request.phoneNumber())
                .password(passwordEncoder.encode(request.password()))
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        // 4. 유저와 연관된 설정 엔티티(연동 계좌, 투자 성향) 초기화 생성
        linkedAccountRepository.save(LinkedAccount.builder().user(user).build());
        investmentProfileRepository.save(InvestmentProfile.builder().user(user).build());

        return issueTokens(user.getId(), true);
    }

    /**
     * 휴대폰 번호와 비밀번호를 확인하여 로그인 처리를 하고 토큰을 발급합니다.
     *
     * @param request 로그인 요청 DTO (휴대폰 번호, 비밀번호)
     * @return 발급된 AccessToken 및 RefreshToken 정보를 담은 TokenResponse
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByPhoneNumberAndStatus(request.phoneNumber(), UserStatus.ACTIVE)
                .orElseThrow(UserNotFoundException::new);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidPasswordException();
        }

        return issueTokens(user.getId(), false);
    }

    /**
     * 주어진 아이디(userId)에 해당하는 사용자의 RefreshToken을 삭제하여 로그아웃 처리합니다.
     *
     * @param userId 로그아웃할 사용자의 아이디
     */
    public void logout(Long userId) {
        log.info("[AuthService] 로그아웃 요청 - userId: {}", userId);
        refreshTokenRepository.deleteById(userId);
        log.info("[AuthService] 로그아웃 처리 완료 - userId: {}", userId);
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

        final Long userId = jwtProvider.getUserId(request.refreshToken());
        final RefreshToken savedToken = refreshTokenRepository.findById(userId)
                .orElseThrow(InvalidTokenException::new);

        if (!savedToken.getToken().equals(request.refreshToken())) {
            throw new InvalidTokenException();
        }
        
        log.info("[AuthService] 토큰 재발급 성공 - userId: {}", userId);

        return issueTokens(userId, false);
    }

    /**
     * 사용자 아이디(userId)를 기반으로 새로운 JWT 토큰 쌍을 발급하고 Redis에 저장합니다.
     *
     * @param userId 토큰을 발급할 사용자 식별자
     * @param isNewUser 신규 가입 유저 여부
     * @return 발급된 토큰 정보를 담은 응답 DTO
     */
    private TokenResponse issueTokens(Long userId, boolean isNewUser) {
        final String newAccessToken = jwtProvider.createAccessToken(userId);
        final String newRefreshToken = jwtProvider.createRefreshToken(userId);

        refreshTokenRepository.save(new RefreshToken(
                userId,
                newRefreshToken,
                jwtProvider.getRefreshTokenExpiration() / 1000
        ));

        return new TokenResponse(newAccessToken, newRefreshToken, isNewUser);
    }
}