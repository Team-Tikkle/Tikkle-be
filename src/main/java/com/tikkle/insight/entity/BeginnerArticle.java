package com.tikkle.insight.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "BEGINNER_ARTICLES")
public class BeginnerArticle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Builder
    public BeginnerArticle(String title, String body, String thumbnailUrl,
                           Integer displayOrder, LocalDateTime publishedAt) {
        this.title = title;
        this.body = body;
        this.thumbnailUrl = thumbnailUrl;
        this.displayOrder = displayOrder;
        this.publishedAt = publishedAt;
    }
}