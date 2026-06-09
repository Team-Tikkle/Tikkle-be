package com.tikkle.insight.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "RECOMMENDED_VIDEOS")
public class RecommendedVideo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "video_url", nullable = false, length = 500)
    private String videoUrl;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "channel_name", length = 100)
    private String channelName;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Builder
    public RecommendedVideo(String title, String videoUrl, String thumbnailUrl,
                            String channelName, Integer displayOrder) {
        this.title = title;
        this.videoUrl = videoUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.channelName = channelName;
        this.displayOrder = displayOrder;
    }
}