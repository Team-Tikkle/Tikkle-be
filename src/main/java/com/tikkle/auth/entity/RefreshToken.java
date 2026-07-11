package com.tikkle.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

@Getter
@AllArgsConstructor
@RedisHash("refresh_token")
public class RefreshToken {
    @Id
    private Long userId;

    private String token;

    @TimeToLive
    private Long expiration;
}