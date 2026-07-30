package com.tikkle.notice.service;

import com.tikkle.notice.dto.response.NoticeDetailResponse;
import com.tikkle.notice.dto.response.NoticeSummaryResponse;
import com.tikkle.notice.exception.NoticeNotFoundException;
import com.tikkle.notice.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 설정 화면의 공지사항 조회를 담당하는 서비스입니다.
 * 공지는 별도의 관리자 페이지 없이 DB에 직접 적재되며, 노출 여부(is_visible)가 켜진 공지만 조회 대상입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {
    private final NoticeRepository noticeRepository;

    /**
     * 노출 중인 공지 목록(본문 제외)을 조회합니다.
     * 상단 고정 공지가 먼저 오고, 그 안에서는 최신 게시일 순으로 정렬됩니다.
     *
     * @return 공지 요약 리스트
     */
    public List<NoticeSummaryResponse> getNotices() {
        return noticeRepository.findVisibleNotices().stream()
                .map(NoticeSummaryResponse::from)
                .toList();
    }

    /**
     * 특정 공지의 상세 내용(본문 포함)을 조회합니다.
     * 숨김 처리된 공지는 ID를 알아도 조회되지 않습니다.
     *
     * @param id 공지 ID
     * @return 공지 상세 내용
     */
    public NoticeDetailResponse getNotice(Long id) {
        return noticeRepository.findByIdAndIsVisibleTrue(id)
                .map(NoticeDetailResponse::from)
                .orElseThrow(NoticeNotFoundException::new);
    }
}