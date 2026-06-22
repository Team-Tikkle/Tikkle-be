package com.tikkle.investment.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MemeAcceptance {
    NONE(0),
    SMALL(10),
    ACTIVE(30);

    private final int maxWeightPercent;
}