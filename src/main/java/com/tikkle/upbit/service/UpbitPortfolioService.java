package com.tikkle.upbit.service;

import com.tikkle.investment.entity.Coin;
import com.tikkle.investment.repository.CoinRepository;
import com.tikkle.upbit.client.UpbitAccountClient;
import com.tikkle.upbit.dto.response.UpbitAccountResponse;
import com.tikkle.upbit.dto.response.UpbitRealtimePortfolioResponse;
import com.tikkle.upbit.util.UpbitAuthUtil;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.exception.LinkedAccountNotFoundException;
import com.tikkle.user.repository.LinkedAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 프론트엔드의 실시간 웹소켓 연동을 지원하기 위해
 * 업비트 API를 직접 호출하여 사용자의 실제 자산 내역을 제공하는 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpbitPortfolioService {
    private final UpbitAccountClient upbitAccountClient;
    private final LinkedAccountRepository linkedAccountRepository;
    private final CoinRepository coinRepository;

    @Transactional(readOnly = true)
    public UpbitRealtimePortfolioResponse getRealtimePortfolio(Long userId) {
        log.info("[UpbitPortfolioService] 사용자 실시간 포트폴리오(업비트) 조회 시작 - userId: {}", userId);

        LinkedAccount linkedAccount = linkedAccountRepository.findByUserId(userId)
                .orElseThrow(LinkedAccountNotFoundException::new);

        // 키의 만료/권한 회수는 아래 업비트 호출이 401을 반환하며 UpbitInvalidKeyException으로 전파된다.
        String token = UpbitAuthUtil.generateToken(
                linkedAccount.getUpbitAccessKey(),
                linkedAccount.getUpbitSecretKey()
        );

        List<UpbitAccountResponse> accounts = upbitAccountClient.getAccounts(token);

        // 자산이 없으면 빈 응답 반환
        if (accounts == null || accounts.isEmpty()) {
            return new UpbitRealtimePortfolioResponse(0L, List.of(), List.of());
        }

        // 마켓 코드 추출 및 코인명 조회를 위한 준비
        List<String> marketCodes = new ArrayList<>();
        for (UpbitAccountResponse account : accounts) {
            marketCodes.add(resolveMarketCode(account));
        }

        // DB에서 코인 한글명 매핑용 데이터 조회
        Map<String, String> coinNameMap = coinRepository.findAllById(marketCodes).stream()
                .collect(Collectors.toMap(Coin::getMarket, Coin::getKoreanName));

        long totalPrincipalAmount = 0L;
        List<UpbitRealtimePortfolioResponse.CoinHoldingDto> holdings = new ArrayList<>();
        List<String> wsMarketCodes = new ArrayList<>();

        for (UpbitAccountResponse account : accounts) {
            String currency = account.currency();
            String marketCode = resolveMarketCode(account);
            
            // 보유 수량
            BigDecimal quantity = account.balance() != null ? account.balance() : BigDecimal.ZERO;
            
            // 매수 평단가
            BigDecimal avgBuyPrice = account.avgBuyPrice() != null ? account.avgBuyPrice() : BigDecimal.ZERO;
            
            // 원화 자산인 경우 원금 = balance, 매수평단가는 1로 간주
            long principalAmount;
            if ("KRW".equalsIgnoreCase(currency)) {
                principalAmount = quantity.setScale(0, RoundingMode.HALF_UP).longValue();
                avgBuyPrice = BigDecimal.ONE; 
            } else {
                // 투자 원금 = 수량 * 평단가 (소수점 반올림 처리)
                principalAmount = quantity.multiply(avgBuyPrice).setScale(0, RoundingMode.HALF_UP).longValue();
                wsMarketCodes.add(marketCode); // 코인만 웹소켓 구독 마켓에 추가
            }

            totalPrincipalAmount += principalAmount;
            
            // 코인 한글명 (DB에 없으면 기본 마켓코드 혹은 통화명 사용)
            String coinName = coinNameMap.getOrDefault(marketCode, "KRW".equalsIgnoreCase(currency) ? "원화" : marketCode);

            holdings.add(new UpbitRealtimePortfolioResponse.CoinHoldingDto(
                    marketCode,
                    coinName,
                    quantity,
                    avgBuyPrice,
                    principalAmount
            ));
        }

        log.info("[UpbitPortfolioService] 포트폴리오 조회 완료 - 총 원금: {}", totalPrincipalAmount);
        
        return new UpbitRealtimePortfolioResponse(totalPrincipalAmount, wsMarketCodes, holdings);
    }

    private String resolveMarketCode(UpbitAccountResponse account) {
        String currency = account.currency();
        if ("KRW".equalsIgnoreCase(currency)) {
            return "KRW";
        }
        String unitCurrency = account.unitCurrency() != null ? account.unitCurrency() : "KRW";
        return unitCurrency + "-" + currency;
    }
}