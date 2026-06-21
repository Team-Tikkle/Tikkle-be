package com.tikkle.upbit.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.tikkle.upbit.exception.UpbitTokenIssueFailedException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UpbitAuthUtil {
    public static String generateToken(String accessKey, String secretKey, String queryString) {
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
            throw new UpbitTokenIssueFailedException();
        }
    }

    public static String generateTokenWithoutQuery(String accessKey, String secretKey) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secretKey);
            String jwtToken = JWT.create()
                    .withClaim("access_key", accessKey)
                    .withClaim("nonce", UUID.randomUUID().toString())
                    .sign(algorithm);
            return "Bearer " + jwtToken;
        } catch (Exception e) {
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