package com.tikkle.notice.controller;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.notice.dto.response.NoticeDetailResponse;
import com.tikkle.notice.dto.response.NoticeSummaryResponse;
import com.tikkle.notice.service.NoticeService;
import com.tikkle.notice.swagger.NoticeSwagger;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 공지사항 도메인의 웹 요청 처리를 담당하는 컨트롤러입니다.
 * 설정 화면의 '공지사항' 진입 시 목록과 상세 내용을 제공합니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notices")
public class NoticeController implements NoticeSwagger {
    private final NoticeService noticeService;

    /**
     * 노출 중인 공지사항 목록을 반환합니다.
     *
     * @return 공지 요약 응답 DTO 리스트 (상단 고정 우선, 최신순)
     */
    @Override
    @GetMapping
    public ApiResponse<List<NoticeSummaryResponse>> getNotices() {
        return ApiResponse.success(noticeService.getNotices());
    }

    /**
     * 특정 공지의 상세 내용(본문 포함)을 반환합니다.
     *
     * @param id 공지 ID
     * @return 공지 상세 응답 DTO
     */
    @Override
    @GetMapping("/{id}")
    public ApiResponse<NoticeDetailResponse> getNotice(@PathVariable Long id) {
        return ApiResponse.success(noticeService.getNotice(id));
    }
}