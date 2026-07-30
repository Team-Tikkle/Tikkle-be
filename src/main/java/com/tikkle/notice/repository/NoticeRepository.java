package com.tikkle.notice.repository;

import com.tikkle.notice.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    @Query("SELECT n FROM Notice n WHERE n.isVisible = true "
            + "ORDER BY n.isPinned DESC, n.publishedAt DESC, n.id DESC")
    List<Notice> findVisibleNotices();

    Optional<Notice> findByIdAndIsVisibleTrue(Long id);
}