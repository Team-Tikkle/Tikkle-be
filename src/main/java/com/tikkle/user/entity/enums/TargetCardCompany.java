package com.tikkle.user.entity.enums;

public enum TargetCardCompany {
    KBANK("케이뱅크");

    private final String companyName;

    TargetCardCompany(String companyName) {
        this.companyName = companyName;
    }

    public String getCompanyName() {
        return companyName;
    }
}