package com.tikkle.insight.fetcher;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Google News RSS 검색 피드에서 뉴스를 수집한다.
 * <p>
 * 구현 함정 대응:
 * - 제목의 " - 매체명" 접미사 제거, 매체명은 source 엘리먼트 우선.
 * - description은 HTML 조각이라 태그를 제거하고 500자로 절단.
 * - 썸네일은 거의 제공되지 않아 대부분 null.
 */
@Slf4j
@Component
public class GoogleNewsRssFetcher implements NewsFetcher {
    private static final String SEARCH_URL = "https://news.google.com/rss/search?q=%s&hl=ko&gl=KR&ceid=KR:ko";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; TikkleBot/1.0)";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;
    private static final int MAX_SUMMARY_LENGTH = 500;

    @Override
    public List<FetchedNewsItem> fetch(String keyword) {
        String url = SEARCH_URL.formatted(URLEncoder.encode(keyword, StandardCharsets.UTF_8));
        try {
            URLConnection connection = URI.create(url).toURL().openConnection();
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);

            try (XmlReader reader = new XmlReader(connection.getInputStream())) {
                SyndFeed feed = new SyndFeedInput().build(reader);
                List<FetchedNewsItem> items = new ArrayList<>();
                for (SyndEntry entry : feed.getEntries()) {
                    items.add(toItem(entry, keyword));
                }
                log.info("Google News RSS 수집 완료. keyword={}, count={}", keyword, items.size());
                return items;
            }
        } catch (Exception e) {
            log.warn("Google News RSS 수집 실패. keyword={}, reason={}", keyword, e.getMessage());
            return List.of();
        }
    }

    private FetchedNewsItem toItem(SyndEntry entry, String keyword) {
        String press = entry.getSource() != null ? entry.getSource().getTitle() : null;
        String rawTitle = entry.getTitle();
        String title = rawTitle;

        if (rawTitle != null) {
            if (press != null && rawTitle.endsWith(" - " + press)) {
                title = rawTitle.substring(0, rawTitle.length() - (" - " + press).length()).trim();
            } else {
                int idx = rawTitle.lastIndexOf(" - ");
                if (idx > 0) {
                    if (press == null) {
                        press = rawTitle.substring(idx + 3).trim();
                    }
                    title = rawTitle.substring(0, idx).trim();
                }
            }
        }

        return new FetchedNewsItem(
                title,
                press,
                entry.getLink(),
                cleanSummary(entry.getDescription() != null ? entry.getDescription().getValue() : null),
                null,
                toLocalDateTime(entry.getPublishedDate()),
                keyword
        );
    }

    private String cleanSummary(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() > MAX_SUMMARY_LENGTH ? text.substring(0, MAX_SUMMARY_LENGTH) : text;
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return LocalDateTime.now(KST);
        }
        return date.toInstant().atZone(KST).toLocalDateTime();
    }
}