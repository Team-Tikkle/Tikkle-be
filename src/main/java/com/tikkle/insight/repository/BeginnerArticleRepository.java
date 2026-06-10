package com.tikkle.insight.repository;

import com.tikkle.insight.entity.BeginnerArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BeginnerArticleRepository extends JpaRepository<BeginnerArticle, Long> {
    List<BeginnerArticle> findAllByOrderByDisplayOrderAsc();
}