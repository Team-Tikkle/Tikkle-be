package com.tikkle.investment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "AI_RECOMMENDATION_HISTORY")
public class AiRecommendationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String profileHashKey;

    @Column(nullable = false)
    private String fngIndex;

    @Column(nullable = false)
    private String btcDominance;

    @Column(nullable = false)
    private String weeklyTrend;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String candidatesJson;

    @Column
    private String hotNarratives;

    @Column(columnDefinition = "TEXT")
    private String macroEvents;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AiRecommendationHistory(String profileHashKey, String fngIndex, String btcDominance, String weeklyTrend, String candidatesJson, String hotNarratives, String macroEvents) {
        this.profileHashKey = profileHashKey;
        this.fngIndex = fngIndex;
        this.btcDominance = btcDominance;
        this.weeklyTrend = weeklyTrend;
        this.candidatesJson = candidatesJson;
        this.hotNarratives = hotNarratives;
        this.macroEvents = macroEvents;
    }
}