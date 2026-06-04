package com.tikkle.auth.service;

import com.tikkle.auth.dto.request.ReissueRequest;
import com.tikkle.auth.dto.response.TokenResponse;
import com.tikkle.auth.entity.RefreshToken;
import com.tikkle.auth.exception.InvalidTokenException;
import com.tikkle.auth.exception.RefreshTokenExpiredException;
import com.tikkle.auth.jwt.JwtProvider;
import com.tikkle.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public void logout(String email) {
        refreshTokenRepository.deleteById(email);
    }

    public TokenResponse reissue(ReissueRequest request) {
        final JwtProvider.TokenValidationResult result = jwtProvider.validateTokenWithResult(request.refreshToken());

        if (result == JwtProvider.TokenValidationResult.INVALID) {
            throw new InvalidTokenException();
        }
        if (result == JwtProvider.TokenValidationResult.EXPIRED) {
            throw new RefreshTokenExpiredException();
        }

        final String email = jwtProvider.getEmail(request.refreshToken());
        final RefreshToken savedToken = refreshTokenRepository.findById(email)
                .orElseThrow(InvalidTokenException::new);

        if (!savedToken.getToken().equals(request.refreshToken())) {
            throw new InvalidTokenException();
        }

        final String newAccessToken = jwtProvider.createAccessToken(email);
        final String newRefreshToken = jwtProvider.createRefreshToken(email);

        refreshTokenRepository.save(new RefreshToken(
                email,
                newRefreshToken,
                jwtProvider.getRefreshTokenExpiration() / 1000
        ));

        return new TokenResponse(newAccessToken, newRefreshToken);
    }
}