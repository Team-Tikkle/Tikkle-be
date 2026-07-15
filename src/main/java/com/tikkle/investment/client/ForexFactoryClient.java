package com.tikkle.investment.client;

import com.tikkle.investment.dto.response.MacroEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 무료로 제공되는 Forex Factory 데이터를 통해 주요 거시경제 지표 일정을 수집하는 클라이언트입니다.
 * API 키가 필요 없는 완전 공개 JSON 엔드포인트를 사용합니다.
 */
@Slf4j
@Component
public class ForexFactoryClient {
    private final RestClient restClient;

    public ForexFactoryClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);

        this.restClient = RestClient.builder()
                .baseUrl("https://nfs.faireconomy.media")
                .requestFactory(factory)
                .build();
    }

    /**
     * 이번 주 거시경제 일정을 가져와 미국(USD)의 High Impact 이벤트만 필터링합니다.
     * 외부 통신 실패 시에는 "Unknown (Data fetch failed)"을 반환합니다.
     *
     * @return 주요 거시경제 일정 문자열
     */
    public String getUpcomingMacroEvents() {
        try {
            MacroEventDto[] eventsArray = restClient.get()
                    .uri("/ff_calendar_thisweek.json")
                    .retrieve()
                    .body(MacroEventDto[].class);

            if (eventsArray != null && eventsArray.length > 0) {
                List<MacroEventDto> events = Arrays.asList(eventsArray);
                String eventString = events.stream()
                        .filter(e -> "USD".equalsIgnoreCase(e.getCountry()) && "High".equalsIgnoreCase(e.getImpact()))
                        .map(e -> String.format("%s at %s", e.getTitle(), e.getDate()))
                        .collect(Collectors.joining(", "));
                        
                return eventString;
            }
            return "Unknown (Data fetch failed)";
        } catch (Exception e) {
            log.warn("[ForexFactoryClient] 거시경제 일정 조회 실패 - errorMessage: {}", e.getMessage());
            return "Unknown (Data fetch failed)";
        }
    }
}