package com.tikkle.insight.dto.response;

import com.tikkle.insight.entity.RecommendedVideo;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천 영상 응답")
public record RecommendedVideoResponse(
        @Schema(description = "영상 ID", example = "1") Long id,
        @Schema(description = "영상 제목", example = "주식 투자 기초 완전정복") String title,
        @Schema(description = "유튜브 외부 링크", example = "https://www.youtube.com/watch?v=xxxx") String videoUrl,
        @Schema(description = "썸네일 URL", example = "https://i.ytimg.com/vi/xxxx/hqdefault.jpg") String thumbnailUrl,
        @Schema(description = "채널명", example = "티끌 투자스쿨") String channelName
) {
    public static RecommendedVideoResponse from(RecommendedVideo video) {
        return new RecommendedVideoResponse(
                video.getId(),
                video.getTitle(),
                video.getVideoUrl(),
                video.getThumbnailUrl(),
                video.getChannelName()
        );
    }
}