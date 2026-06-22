package com.tikkle.investment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "COIN_METADATA")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coin {
    @Id
    @Column(name = "market", length = 20, nullable = false)
    private String market;

    @Column(name = "korean_name", length = 100, nullable = false)
    private String koreanName;

    @Column(name = "english_name", length = 100, nullable = false)
    private String englishName;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Coin(String market, String koreanName, String englishName) {
        this.market = market;
        this.koreanName = koreanName;
        this.englishName = englishName;
    }

    public void updateNames(String koreanName, String englishName) {
        this.koreanName = koreanName;
        this.englishName = englishName;
    }
}