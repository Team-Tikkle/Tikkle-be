package com.tikkle.upbit.client;

import com.tikkle.upbit.dto.response.UpbitTickerResponse;
import com.tikkle.upbit.exception.UpbitTickerInquiryFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 특정 마켓(코인)의 현재가(Ticker) 정보를 조회하는 외부 연동 클라이언트입니다.
 * 포트폴리오 수익률 계산, 코인 추천 등 실시간 시세가 필요한 곳에서 호출됩니다.
 */
@Slf4j
@Component
public class UpbitTickerClient {
    // 업비트 시세 API는 URI가 길면 414를 반환하므로 한 번에 보내는 마켓 코드 수를 제한한다
    private static final int CHUNK_SIZE = 100;

    private final RestClient restClient;

    public UpbitTickerClient(RestClient upbitRestClient) {
        this.restClient = upbitRestClient;
    }

    /**
     * 쉼표(,)로 구분된 마켓 코드 목록을 받아 해당 코인들의 현재 시세(Ticker)를 조회합니다.
     * 마켓 코드가 많으면 여러 번에 나눠 호출하며, 일부 묶음만 실패한 경우 조회에 성공한 시세만 반환합니다.
     *
     * @param markets 조회할 마켓 코드 목록 (예: "KRW-BTC,KRW-ETH")
     * @return 시세 정보 리스트
     * @throws UpbitTickerInquiryFailedException 모든 묶음의 시세 조회에 실패한 경우
     */
    public List<UpbitTickerResponse> getTickers(String markets) {
        List<String> marketCodes = Arrays.stream(markets.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .toList();

        if (marketCodes.isEmpty()) {
            return Collections.emptyList();
        }

        List<UpbitTickerResponse> tickers = new ArrayList<>();
        int failedChunkCount = 0;
        int totalChunkCount = 0;

        for (int start = 0; start < marketCodes.size(); start += CHUNK_SIZE) {
            List<String> chunk = marketCodes.subList(start, Math.min(start + CHUNK_SIZE, marketCodes.size()));
            totalChunkCount++;
            try {
                tickers.addAll(fetchChunk(chunk));
            } catch (Exception e) {
                // 상장폐지된 코드가 하나라도 섞이면 해당 묶음 전체가 404로 실패하므로, 나머지 묶음은 계속 조회한다
                failedChunkCount++;
                log.error("[UpbitTickerClient] 업비트 시세 조회 실패 - markets: {}", String.join(",", chunk), e);
            }
        }

        if (failedChunkCount == totalChunkCount) {
            throw new UpbitTickerInquiryFailedException();
        }
        if (failedChunkCount > 0) {
            log.warn("[UpbitTickerClient] 업비트 시세 부분 조회 - 조회 건수: {}, 실패 묶음: {}/{}",
                    tickers.size(), failedChunkCount, totalChunkCount);
        }
        return tickers;
    }

    private List<UpbitTickerResponse> fetchChunk(List<String> marketCodes) {
        List<UpbitTickerResponse> response = restClient.get()
                .uri("/v1/ticker?markets=" + String.join(",", marketCodes))
                .retrieve()
                .body(new ParameterizedTypeReference<List<UpbitTickerResponse>>() {});
        return response != null ? response : Collections.emptyList();
    }
}