package com.tikkle.investment.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TrendSensitivity {
    FUNDAMENTAL_ONLY(2),
    PARTIAL_TREND(5),
    FULL_TREND(9);

    private final int score;
}