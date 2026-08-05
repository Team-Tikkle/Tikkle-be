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

    // 기존 행이 상장폐지로 오인되지 않도록 DEFAULT TRUE로 컬럼을 추가한다
    @Column(name = "is_active", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT TRUE")
    private boolean isActive;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Coin(String market, String koreanName, String englishName) {
        this.market = market;
        this.koreanName = koreanName;
        this.englishName = englishName;
        this.isActive = true;
    }

    public void updateNames(String koreanName, String englishName) {
        this.koreanName = koreanName;
        this.englishName = englishName;
    }

    // 업비트에서 상장폐지된 코인. 과거 결제 원장이 FK로 참조하므로 행은 남기고 비활성화만 한다
    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }
}