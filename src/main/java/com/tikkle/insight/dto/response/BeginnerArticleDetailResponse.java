package com.tikkle.insight.dto.response;

import com.tikkle.insight.entity.BeginnerArticle;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "초보자 글 상세 응답 (본문 포함, 앱 내부 렌더링용)")
public record BeginnerArticleDetailResponse(
        @Schema(description = "글 ID", example = "1") Long id,
        @Schema(description = "글 제목", example = "주식을 처음 시작한다면") String title,
        @Schema(description = "본문 전체", example = "주식 투자는...") String body,
        @Schema(description = "썸네일 URL (없을 수 있음)", example = "null") String thumbnailUrl,
        @Schema(description = "게시일시", example = "2026-06-01T00:00:00") LocalDateTime publishedAt
) {
    public static BeginnerArticleDetailResponse from(BeginnerArticle article) {
        return new BeginnerArticleDetailResponse(
                article.getId(),
                article.getTitle(),
                article.getBody(),
                article.getThumbnailUrl(),
                article.getPublishedAt()
        );
    }
}