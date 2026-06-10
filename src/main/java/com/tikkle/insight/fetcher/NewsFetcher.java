package com.tikkle.insight.fetcher;

import java.util.List;

/**
 * 뉴스 수집 추상화.
 * 현재 구현체는 {@link GoogleNewsRssFetcher} 하나.
 * Google News RSS가 실제로 차단되는 시점에만 공식 API 기반 구현체(예: 네이버 뉴스 검색 API)를 추가한다.
 */
public interface NewsFetcher {
    /**
     * 키워드로 뉴스를 수집한다. 외부 호출 실패 시 빈 리스트를 반환한다(예외를 던지지 않음).
     */
    List<FetchedNewsItem> fetch(String keyword);
}