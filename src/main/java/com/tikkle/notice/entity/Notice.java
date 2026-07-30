package com.tikkle.notice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "NOTICES")
public class Notice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 관리자 페이지 없이 DB에 직접 INSERT 하므로, 플래그 두 개는 DB 기본값을 두어 INSERT 문에서 생략할 수 있게 한다.
    @ColumnDefault("false")
    @Column(name = "is_pinned", nullable = false)
    private boolean isPinned = false;

    @ColumnDefault("true")
    @Column(name = "is_visible", nullable = false)
    private boolean isVisible = true;

    // 앱에 표시되는 게시일시 겸 목록 정렬 기준. 과거 날짜로 등록할 수도 있어 기본값 없이 INSERT 시 명시한다.
    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    // 생략 시 DB 기본값(is_pinned=false, is_visible=true)과 동일하게 동작하도록 래퍼 타입으로 받아 null 을 보정한다.
    @Builder
    private Notice(String title, String content, Boolean isPinned, Boolean isVisible, LocalDateTime publishedAt) {
        this.title = title;
        this.content = content;
        this.isPinned = isPinned != null && isPinned;
        this.isVisible = isVisible == null || isVisible;
        this.publishedAt = publishedAt != null ? publishedAt : LocalDateTime.now();
    }
}