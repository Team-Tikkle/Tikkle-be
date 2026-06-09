package com.tikkle.insight.fetcher;

import java.time.LocalDateTime;

/**
 * 외부 뉴스 소스에서 수집한 기사 1건. (저장 전 정제된 형태)
 */
public record FetchedNewsItem(
        String title,
        String press,
        String link,
        String summary,
        String thumbnailUrl,
        LocalDateTime publishedAt,
        String keyword
) {}