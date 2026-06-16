package com.tikkle.insight.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "MARKET_TOPICS")
public class MarketTopic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "press", length = 100)
    private String press;

    @Column(name = "link", nullable = false, unique = true, length = 500)
    private String link;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "keyword", length = 50)
    private String keyword;

    @CreationTimestamp
    @Column(name = "fetched_at", nullable = false, updatable = false)
    private LocalDateTime fetchedAt;

    @Builder
    public MarketTopic(String title, String press, String link,
                       String thumbnailUrl, LocalDateTime publishedAt, String keyword) {
        this.title = title;
        this.press = press;
        this.link = link;
        this.thumbnailUrl = thumbnailUrl;
        this.publishedAt = publishedAt;
        this.keyword = keyword;
    }
}