package com.tikkle.user.entity.enums;

public enum TwoFactorProvider {
    KAKAO("kakao"),
    NAVER("naver"),
    HANA("hana");

    private final String upbitProviderType;

    TwoFactorProvider(String upbitProviderType) {
        this.upbitProviderType = upbitProviderType;
    }

    public String getUpbitProviderType() {
        return upbitProviderType;
    }
}