package com.tikkle.insight.dto.response;

import com.tikkle.insight.entity.MarketTopic;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "투데이 마켓 토픽(뉴스) 응답")
public record MarketTopicResponse(
        @Schema(description = "토픽 ID", example = "1") Long id,
        @Schema(description = "기사 제목", example = "코스피, 외국인 매수에 2,700선 회복") String title,
        @Schema(description = "매체명", example = "한국경제") String press,
        @Schema(description = "원문 외부 링크", example = "https://news.google.com/rss/articles/...") String link,
        @Schema(description = "썸네일 URL (없을 수 있음)", example = "null") String thumbnailUrl,
        @Schema(description = "발행일시", example = "2026-06-09T08:30:00") LocalDateTime publishedAt
) {
    public static MarketTopicResponse from(MarketTopic topic) {
        return new MarketTopicResponse(
                topic.getId(),
                topic.getTitle(),
                topic.getPress(),
                topic.getLink(),
                topic.getThumbnailUrl(),
                topic.getPublishedAt()
        );
    }
}