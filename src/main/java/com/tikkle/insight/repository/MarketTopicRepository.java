package com.tikkle.insight.repository;

import com.tikkle.insight.entity.MarketTopic;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MarketTopicRepository extends JpaRepository<MarketTopic, Long> {
    boolean existsByLink(String link);

    List<MarketTopic> findTop30ByOrderByPublishedAtDesc();

    long deleteByFetchedAtBefore(LocalDateTime threshold);

    // 최신순으로 유지할 id만 조회 (Pageable로 개수 제한). fetchedAt은 @CreationTimestamp라 NULL 없음.
    @Query("SELECT m.id FROM MarketTopic m ORDER BY m.fetchedAt DESC")
    List<Long> findIdsOrderByFetchedAtDesc(Pageable pageable);

    @Modifying
    @Query("DELETE FROM MarketTopic m WHERE m.id NOT IN :ids")
    int deleteByIdNotIn(@Param("ids") List<Long> ids);
}