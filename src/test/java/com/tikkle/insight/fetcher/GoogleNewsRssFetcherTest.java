package com.tikkle.insight.fetcher;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 화이트리스트 필터의 전제(ROME이 RSS {@code <source url="...">}의 url 속성을 노출한다)를 검증한다.
 * 이 매핑이 깨지면 모든 항목의 출처 호스트가 null이 되어 전부 제외되므로(무음 실패) 회귀 테스트로 둔다.
 */
class GoogleNewsRssFetcherTest {

    private static final String RSS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>test</title>
                <item>
                  <title>비트코인 기사 - 조선비즈</title>
                  <link>https://news.google.com/rss/articles/TOKEN</link>
                  <source url="https://biz.chosun.com">조선비즈</source>
                </item>
              </channel>
            </rss>
            """;

    @Test
    @DisplayName("ROME은 <source>의 url 속성을 SyndEntry.getSource().getUri()로 노출한다")
    void sourceUrlIsExposedAsUri() throws Exception {
        SyndFeed feed = new SyndFeedInput().build(new StringReader(RSS));
        SyndEntry entry = feed.getEntries().get(0);

        assertThat(entry.getSource()).isNotNull();
        assertThat(entry.getSource().getUri()).isEqualTo("https://biz.chosun.com");
        assertThat(entry.getSource().getTitle()).isEqualTo("조선비즈");
    }
}
