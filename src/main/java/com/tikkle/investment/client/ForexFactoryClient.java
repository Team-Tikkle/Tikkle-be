package com.tikkle.investment.client;

import com.tikkle.investment.dto.response.MacroEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 무료로 제공되는 Forex Factory 데이터를 통해 주요 거시경제 지표 일정을 수집하는 클라이언트입니다.
 * API 키가 필요 없는 완전 공개 JSON 엔드포인트를 사용합니다.
 */
@Slf4j
@Component
public class ForexFactoryClient {
    // 48시간 내 지표가 없는 것은 정상 결과다. 빈 문자열로 돌려주면 수집 실패로 오인되고,
    // 프롬프트의 Upcoming Macro Events 항목이 빈칸인 채로 AI에게 전달된다
    private static final String NO_UPCOMING_EVENTS = "None scheduled in the next 48 hours.";
    private static final String FETCH_FAILED = "Unknown (Data fetch failed)";

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
     * 이번 주 거시경제 일정을 가져와 미국(USD)의 High Impact 이벤트 중 향후 48시간 내의 것만 필터링합니다.
     * 지표가 하나도 없는 것은 정상 결과이므로 "없음"을 명시해 반환하고, 외부 통신 실패와 구분합니다.
     * 경제 지표는 주 중반에 몰려 있어 금~일 실행분은 대부분 "없음"이 정상입니다.
     *
     * @return 주요 거시경제 일정 문자열 (없으면 "None scheduled...", 실패 시 "Unknown (Data fetch failed)")
     */
    public String getUpcomingMacroEvents() {
        try {
            MacroEventDto[] eventsArray = restClient.get()
                    .uri("/ff_calendar_thisweek.json")
                    .retrieve()
                    .body(MacroEventDto[].class);

            if (eventsArray == null || eventsArray.length == 0) {
                log.warn("[ForexFactoryClient] 거시경제 일정 응답이 비어 있습니다");
                return FETCH_FAILED;
            }

            List<MacroEventDto> events = Arrays.asList(eventsArray);
            Instant now = Instant.now();
            Instant limit = now.plus(48, ChronoUnit.HOURS);

            List<MacroEventDto> usdHighEvents = events.stream()
                    .filter(e -> "USD".equalsIgnoreCase(e.getCountry()) && "High".equalsIgnoreCase(e.getImpact()))
                    .toList();

            String eventString = usdHighEvents.stream()
                    .filter(e -> {
                        try {
                            Instant eventTime = OffsetDateTime.parse(e.getDate()).toInstant();
                            return !eventTime.isBefore(now) && !eventTime.isAfter(limit);
                        } catch (Exception ex) {
                            log.warn("[ForexFactoryClient] 이벤트 날짜 파싱 실패 - date: {}", e.getDate());
                            return false;
                        }
                    })
                    .map(e -> String.format("%s at %s", e.getTitle(), e.getDate()))
                    .collect(Collectors.joining(", "));

            if (eventString.isBlank()) {
                // 전체/USD High 건수를 함께 남겨야 "지표가 없는 정상 상태"와
                // "역직렬화가 깨져 country/impact 가 전부 null 이 된 상태"를 구분할 수 있다
                log.info("[ForexFactoryClient] 48시간 내 미국 High Impact 지표 없음 - 전체: {}건, USD High: {}건",
                        events.size(), usdHighEvents.size());
                return NO_UPCOMING_EVENTS;
            }

            return eventString;
        } catch (Exception e) {
            log.warn("[ForexFactoryClient] 거시경제 일정 조회 실패 - errorMessage: {}", e.getMessage());
            return FETCH_FAILED;
        }
    }
}