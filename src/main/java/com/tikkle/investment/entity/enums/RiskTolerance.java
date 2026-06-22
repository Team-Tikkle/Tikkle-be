package com.tikkle.investment.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RiskTolerance {
    SELL_IMMEDIATELY(2),
    HOLD(5),
    BUY_MORE(9);

    private final int score;
}