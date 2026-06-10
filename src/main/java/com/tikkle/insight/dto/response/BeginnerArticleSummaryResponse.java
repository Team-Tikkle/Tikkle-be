package com.tikkle.insight.dto.response;

import com.tikkle.insight.entity.BeginnerArticle;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "초보자 글 목록 응답 (본문 제외)")
public record BeginnerArticleSummaryResponse(
        @Schema(description = "글 ID", example = "1") Long id,
        @Schema(description = "글 제목", example = "주식을 처음 시작한다면") String title,
        @Schema(description = "목록 썸네일 URL (없을 수 있음)", example = "null") String thumbnailUrl,
        @Schema(description = "게시일시", example = "2026-06-01T00:00:00") LocalDateTime publishedAt
) {
    public static BeginnerArticleSummaryResponse from(BeginnerArticle article) {
        return new BeginnerArticleSummaryResponse(
                article.getId(),
                article.getTitle(),
                article.getThumbnailUrl(),
                article.getPublishedAt()
        );
    }
}