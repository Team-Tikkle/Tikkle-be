package com.tikkle.upbit.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.tikkle.upbit.exception.UpbitAuthParamException;
import com.tikkle.upbit.exception.UpbitTokenIssueFailedException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * 업비트 API 호출 시 필요한 JWT 인증 토큰을 생성하는 유틸리티 클래스입니다.
 * 인스턴스화할 수 없으며 정적 메서드만 제공합니다.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UpbitAuthUtil {
    
    /**
     * 쿼리 파라미터가 없는 요청에 사용할 JWT 토큰을 생성합니다.
     *
     * @param accessKey 업비트 API Access Key
     * @param secretKey 업비트 API Secret Key
     * @return 생성된 Bearer 토큰 문자열
     * @throws UpbitTokenIssueFailedException 토큰 생성 중 오류 발생 시
     */
    public static String generateToken(String accessKey, String secretKey) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secretKey);
            String jwtToken = JWT.create()
                    .withClaim("access_key", accessKey)
                    .withClaim("nonce", UUID.randomUUID().toString())
                    .sign(algorithm);
            return "Bearer " + jwtToken;
        } catch (Exception e) {
            log.error("[UpbitAuthUtil] 토큰 발급 실패 - error: {}", e.getMessage(), e);
            throw new UpbitTokenIssueFailedException();
        }
    }

    /**
     * 쿼리 파라미터가 포함된 요청에 사용할 JWT 토큰을 생성합니다.
     * 쿼리 파라미터를 SHA-512로 해싱하여 토큰 클레임에 포함시킵니다.
     *
     * @param accessKey 업비트 API Access Key
     * @param secretKey 업비트 API Secret Key
     * @param queryString 쿼리 파라미터 문자열 (예: "market=KRW-BTC&side=bid")
     * @return 생성된 Bearer 토큰 문자열
     * @throws UpbitAuthParamException 쿼리 문자열이 비어있는 경우
     * @throws UpbitTokenIssueFailedException 토큰 생성 또는 해싱 중 오류 발생 시
     */
    public static String generateToken(String accessKey, String secretKey, String queryString) {
        if (queryString == null || queryString.trim().isEmpty()) {
            throw new UpbitAuthParamException();
        }
        try {
            Algorithm algorithm = Algorithm.HMAC256(secretKey);
            String jwtToken = JWT.create()
                    .withClaim("access_key", accessKey)
                    .withClaim("nonce", UUID.randomUUID().toString())
                    .withClaim("query_hash", getSHA512(queryString))
                    .withClaim("query_hash_alg", "SHA512")
                    .sign(algorithm);
            return "Bearer " + jwtToken;
        } catch (Exception e) {
            log.error("[UpbitAuthUtil] 토큰 발급 실패 - error: {}", e.getMessage(), e);
            throw new UpbitTokenIssueFailedException();
        }
    }

    private static String getSHA512(String plainText) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] bytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new UpbitTokenIssueFailedException();
        }
    }
}