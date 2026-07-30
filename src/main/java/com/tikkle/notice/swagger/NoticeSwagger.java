package com.tikkle.notice.swagger;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.notice.dto.response.NoticeDetailResponse;
import com.tikkle.notice.dto.response.NoticeSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Notice", description = "공지사항 API")
public interface NoticeSwagger {
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "공지사항 목록 조회",
            description = "노출 중인 공지사항 목록(본문 제외)을 조회합니다. "
                    + "상단 고정(isPinned=true) 공지가 먼저 오고, 그 안에서는 게시일시 내림차순으로 정렬됩니다.")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "조회 성공",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                    {
                      "code": "SUCCESS",
                      "message": "요청에 성공했습니다.",
                      "data": [
                        {
                          "id": 3,
                          "title": "[필독] 서비스 점검 안내",
                          "isPinned": true,
                          "publishedAt": "2026-07-28T09:00:00"
                        },
                        {
                          "id": 2,
                          "title": "티끌 v1.2.0 업데이트 안내",
                          "isPinned": false,
                          "publishedAt": "2026-07-20T10:00:00"
                        }
                      ]
                    }
                    """))))
    ApiResponse<List<NoticeSummaryResponse>> getNotices();

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "공지사항 상세 조회",
            description = "공지 본문 전체를 조회합니다. 본문은 플레인 텍스트이며 `\\n\\n` 으로 문단을 구분합니다. "
                    + "숨김 처리된 공지는 404(NOTICE-001)를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "code": "SUCCESS",
                              "message": "요청에 성공했습니다.",
                              "data": {
                                "id": 3,
                                "title": "[필독] 서비스 점검 안내",
                                "content": "안녕하세요, 티끌입니다.\\n\\n서비스 안정화를 위한 점검을 진행합니다.",
                                "isPinned": true,
                                "publishedAt": "2026-07-28T09:00:00"
                              }
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "공지를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "code": "NOTICE-001",
                              "message": "공지사항을 찾을 수 없습니다."
                            }
                            """)))
    })
    ApiResponse<NoticeDetailResponse> getNotice(
            @Parameter(description = "공지 ID", example = "3") Long id);
}