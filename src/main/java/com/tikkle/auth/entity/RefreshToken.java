package com.tikkle.auth.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RedisHash("refresh_token")
public class RefreshToken {
    @Id
    private Long userId;

    private String token;

    @TimeToLive
    private Long expiration;

    @Builder
    private RefreshToken(Long userId, String token, Long expiration) {
        this.userId = userId;
        this.token = token;
        this.expiration = expiration;
    }
}