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
import com.tikkle.user.repository.LinkedAccountRepository;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.tikkle.auth.dto.request.ResetPasswordRequest;
import com.tikkle.auth.entity.enums.SmsPurpose;

/**
 * 회원가입, 로그인, 로그아웃, 토큰 재발급 등 인증 관련 비즈니스 로직을 처리하는 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
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
        // 1. 휴대폰 인증 토큰을 가장 먼저 검증한다 (이 시점에는 소모하지 않는다).
        // 중복 가입 검사를 앞에 두면 아무 토큰이나 넣어도 409/400 차이로 가입 여부가 노출된다.
        smsService.validateSignupToken(request.phoneNumber(), request.signupToken());

        // 2. 인증된 요청에 한해 중복 가입 여부를 확인한다
        if (userRepository.findByPhoneNumber(request.phoneNumber()).isPresent()) {
            throw new PhoneAlreadyRegisteredException();
        }

        // 3. 비밀번호 해싱 및 유저 생성
        User user = User.builder()
                .name(request.name())
                .phoneNumber(request.phoneNumber())
                .password(passwordEncoder.encode(request.password()))
                .build();
        try {
            // 동시 요청이 1번 검사를 함께 통과한 경우 UNIQUE 제약으로 걸러내 500 대신 409를 반환한다
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            log.warn("[AuthService] 동시성 중복 가입 감지 - phoneNumber: {}", request.phoneNumber());
            throw new PhoneAlreadyRegisteredException();
        }

        // 4. 유저와 연관된 설정 엔티티(연동 계좌, 투자 성향) 초기화 생성
        linkedAccountRepository.save(LinkedAccount.builder().user(user).build());
        investmentProfileRepository.save(InvestmentProfile.builder().user(user).build());

        // 5. 가입이 확정된 뒤에만 인증 토큰을 소모한다
        consumeTokenAfterCommit(request.phoneNumber(), SmsPurpose.SIGNUP);

        log.info("[AuthService] 회원가입 완료 - userId: {}", user.getId());

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
        User user = userRepository.findByPhoneNumber(request.phoneNumber())
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
     * 비밀번호 재설정을 위한 SMS 인증번호 발송 요청.
     * 미가입 번호라도 정상 응답(200)을 반환하여 가입 여부가 노출되는 사용자 열거를 방지합니다.
     *
     * @param phoneNumber 휴대폰 번호
     */
    public void sendPasswordResetSms(String phoneNumber) {
        if (userRepository.findByPhoneNumber(phoneNumber).isEmpty()) {
            // 발송하지 않더라도 쿼터는 동일하게 소모해야 응답/쿨다운 차이로 가입 여부를 추론할 수 없다
            smsService.consumeSendQuota(phoneNumber, SmsPurpose.PASSWORD_RESET);
            log.info("[AuthService] 미가입 번호의 비밀번호 재설정 요청 - 발송 생략(사용자 열거 방지) - phoneNumber: {}", phoneNumber);
            return;
        }

        smsService.sendVerificationCode(phoneNumber, SmsPurpose.PASSWORD_RESET);
    }

    /**
     * 비밀번호 재설정을 위한 SMS 인증번호 검증 및 토큰 발급
     *
     * @param phoneNumber 휴대폰 번호
     * @param code 인증번호
     * @return 발급된 재설정용 토큰
     */
    public String verifyPasswordResetSms(String phoneNumber, String code) {
        return smsService.verifyCodeAndGetToken(phoneNumber, code, SmsPurpose.PASSWORD_RESET);
    }

    /**
     * 검증된 토큰을 사용하여 새로운 비밀번호로 재설정합니다.
     *
     * @param request 비밀번호 재설정 요청 DTO
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // 1. 재설정 토큰을 가장 먼저 검증한다 (이 시점에는 소모하지 않는다).
        // 유저 조회를 앞에 두면 아무 토큰이나 넣어도 404/400 차이로 가입 여부가 노출되어
        // sendPasswordResetSms의 사용자 열거 방지가 그대로 우회된다.
        smsService.validatePasswordResetToken(request.phoneNumber(), request.resetToken());

        // 2. 인증된 요청에 한해 유저를 조회한다
        User user = userRepository.findByPhoneNumber(request.phoneNumber())
                .orElseThrow(UserNotFoundException::new);

        // 3. 비밀번호 변경 로직 (더티 체킹)
        user.updatePassword(passwordEncoder.encode(request.newPassword()));

        // 4. 기존 세션 무효화 - 계정 탈취 상황에서 공격자의 리프레시 토큰을 즉시 끊는다
        refreshTokenRepository.deleteById(user.getId());

        // 5. 재설정이 확정된 뒤에만 인증 토큰을 소모한다
        consumeTokenAfterCommit(request.phoneNumber(), SmsPurpose.PASSWORD_RESET);

        log.info("[AuthService] 비밀번호 재설정 완료 및 기존 세션 무효화 - userId: {}", user.getId());
    }

    /**
     * DB 트랜잭션이 커밋된 뒤에 SMS 임시 토큰을 소모합니다.
     * Redis는 트랜잭션 롤백 대상이 아니므로, 커밋 전에 삭제하면 후속 실패 시
     * 사용자가 아무 잘못 없이 SMS 인증을 다시 해야 하는 문제가 생깁니다.
     */
    private void consumeTokenAfterCommit(String phoneNumber, SmsPurpose purpose) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    smsService.consumeToken(phoneNumber, purpose);
                } catch (Exception e) {
                    // 삭제 실패해도 토큰은 TTL(30분)로 만료되며, 중복 사용은 선행 검증 단계에서 차단된다
                    log.error("[AuthService] 커밋 후 SMS 임시 토큰 삭제 실패 - phoneNumber: {}, purpose: {}", phoneNumber, purpose, e);
                }
            }
        });
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