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
import java.util.Set;

/**
 * Google News RSS 검색 피드에서 뉴스를 수집한다.
 * <p>
 * 구현 함정 대응:
 * - 제목의 " - 매체명" 접미사 제거, 매체명은 source 엘리먼트 우선.
 * - 썸네일은 거의 제공되지 않아 대부분 null.
 * - Google News 검색 피드에는 언론사 기사 외에 커뮤니티/블로그/외국어 자동수집 글도 섞여 들어온다.
 *   커뮤니티 출처는 무한히 늘어나 차단 목록으로는 못 따라잡으므로, 신뢰 언론사 도메인만 통과시키는
 *   {@link #ALLOWED_SOURCE_HOSTS} 화이트리스트로 거른다. (목록에 없는 출처는 안전하게 제외.)
 */
@Slf4j
@Component
public class GoogleNewsRssFetcher implements NewsFetcher {
    private static final String SEARCH_URL = "https://news.google.com/rss/search?q=%s&hl=ko&gl=KR&ceid=KR:ko";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; TikkleBot/1.0)";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    /**
     * 수집을 허용할 신뢰 한국 언론사 출처 도메인.
     * {@code <source>}의 호스트가 이 목록의 도메인이거나 그 서브도메인(예: {@code biz.chosun.com})이면 통과시킨다.
     * 새 신뢰 언론사가 보이면 여기에 추가한다.
     * <p>
     * 의도적으로 제외: 크립토 전문지/Investing.com(기사+분석·커뮤니티 혼재, 경로 구분 불가),
     * 코인니스, 포털(v.daum.net·news.nate.com, 원 언론사 불명확), CoinDesk(영문),
     * 트레이딩뷰/네이버블로그/프리미엄콘텐츠/외국어 자동수집(커뮤니티·비기사).
     */
    private static final Set<String> ALLOWED_SOURCE_HOSTS = Set.of(
            // 종합·경제 일간지/통신
            "yna.co.kr", "news1.kr", "newsis.com", "newspim.com", "edaily.co.kr",
            "hankyung.com", "mk.co.kr", "joongang.co.kr", "chosun.com", "munhwa.com",
            "asiae.co.kr", "hani.co.kr", "khan.co.kr", "seoul.co.kr", "hankookilbo.com",
            "etoday.co.kr", "newsway.co.kr", "fntimes.com",
            // 방송·경제방송
            "kbs.co.kr", "sbs.co.kr", "mtn.co.kr", "yonhapnewstv.co.kr",
            // IT·금융 전문지
            "etnews.com", "zdnet.co.kr", "digitaltoday.co.kr", "inews24.com",
            "bizwatch.co.kr", "einfomax.co.kr"
    );

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
                    if (!isAllowedSource(entry)) {
                        continue;
                    }
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
                null,
                toLocalDateTime(entry.getPublishedDate()),
                keyword
        );
    }

    /**
     * 항목의 {@code <source>} 호스트가 화이트리스트(또는 그 서브도메인)에 속하는지 판정한다.
     * 출처를 확인할 수 없는 항목은 안전하게 제외한다(false).
     */
    private boolean isAllowedSource(SyndEntry entry) {
        String host = sourceHost(entry);
        if (host == null) {
            return false;
        }
        for (String allowed : ALLOWED_SOURCE_HOSTS) {
            if (host.equals(allowed) || host.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    /** RSS {@code <source url="...">}의 호스트를 소문자로 반환한다. 없거나 파싱 실패 시 null. */
    private String sourceHost(SyndEntry entry) {
        if (entry.getSource() == null) {
            return null;
        }
        String url = entry.getSource().getUri();
        if (url == null) {
            url = entry.getSource().getLink();
        }
        if (url == null) {
            return null;
        }
        try {
            String host = URI.create(url).getHost();
            return host == null ? null : host.toLowerCase();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return LocalDateTime.now(KST);
        }
        return date.toInstant().atZone(KST).toLocalDateTime();
    }
}