package com.tikkle.insight.repository;

import com.tikkle.insight.entity.RecommendedVideo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendedVideoRepository extends JpaRepository<RecommendedVideo, Long> {
    List<RecommendedVideo> findAllByOrderByDisplayOrderAsc();
}