package com.tikkle.notice.dto.response;

import com.tikkle.notice.entity.Notice;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "공지사항 상세 응답 (본문 포함, 앱 내부 렌더링용)")
public record NoticeDetailResponse(
        @Schema(description = "공지 ID", example = "1") Long id,
        @Schema(description = "공지 제목", example = "티끌 v1.2.0 업데이트 안내") String title,
        @Schema(description = "본문 전체 (플레인 텍스트, \\n\\n 으로 문단 구분)",
                example = "안녕하세요, 티끌입니다.\n\n이번 업데이트에서는...") String content,
        @Schema(description = "상단 고정 여부", example = "true") boolean isPinned,
        @Schema(description = "게시일시", example = "2026-07-30T10:00:00") LocalDateTime publishedAt
) {
    public static NoticeDetailResponse from(Notice notice) {
        return new NoticeDetailResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.isPinned(),
                notice.getPublishedAt()
        );
    }
}