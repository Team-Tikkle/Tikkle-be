package com.tikkle.investment.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MacroEventDto {
    private String title;
    private String date;
    private String country;
    private String impact;
}