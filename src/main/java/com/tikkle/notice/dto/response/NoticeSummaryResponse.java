package com.tikkle.notice.dto.response;

import com.tikkle.notice.entity.Notice;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "공지사항 목록 응답 (본문 제외)")
public record NoticeSummaryResponse(
        @Schema(description = "공지 ID", example = "1") Long id,
        @Schema(description = "공지 제목", example = "티끌 v1.2.0 업데이트 안내") String title,
        @Schema(description = "상단 고정 여부 (true 인 항목이 목록 최상단에 노출됨)", example = "true") boolean isPinned,
        @Schema(description = "게시일시", example = "2026-07-30T10:00:00") LocalDateTime publishedAt
) {
    public static NoticeSummaryResponse from(Notice notice) {
        return new NoticeSummaryResponse(
                notice.getId(),
                notice.getTitle(),
                notice.isPinned(),
                notice.getPublishedAt()
        );
    }
}