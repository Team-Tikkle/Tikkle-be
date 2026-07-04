package com.tikkle.insight.seed;

import com.tikkle.insight.entity.BeginnerArticle;
import com.tikkle.insight.entity.InvestmentTerm;
import com.tikkle.insight.entity.RecommendedVideo;
import com.tikkle.insight.repository.BeginnerArticleRepository;
import com.tikkle.insight.repository.InvestmentTermRepository;
import com.tikkle.insight.repository.RecommendedVideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

/**
 * 용어집 / 초보자 글 / 추천 영상 시드 데이터.
 * 각 테이블이 비어있을 때만 1회 적재(count==0 가드)하여 재실행에 안전하다.
 *
 * 초기 큐레이션 콘텐츠다. 값을 바꾸려면 해당 테이블을 비우고 재기동하거나 DB를 직접 수정한다
 * (count==0 가드 때문에 코드만 고쳐서는 기존 데이터가 갱신되지 않음).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsightDataSeeder implements ApplicationRunner {
    private final InvestmentTermRepository investmentTermRepository;
    private final BeginnerArticleRepository beginnerArticleRepository;
    private final RecommendedVideoRepository recommendedVideoRepository;

    @Override
    public void run(ApplicationArguments args) {
        seedTerms();
        seedArticles();
        seedVideos();
    }

    private void seedTerms() {
        if (investmentTermRepository.count() > 0) {
            return;
        }
        List<InvestmentTerm> terms = ordered(
                o -> term("가상자산(암호화폐)",
                        "블록체인 기반으로 발행·거래되는 디지털 자산입니다. 중앙은행이나 특정 기관이 아니라 분산된 네트워크가 발행과 거래 기록을 관리합니다.", o),
                o -> term("비트코인(BTC)",
                        "2009년 등장한 최초의 가상자산입니다. 발행량이 2,100만 개로 정해져 있고, 시가총액이 가장 커 시장 전체 분위기를 보는 기준이 됩니다.", o),
                o -> term("알트코인",
                        "비트코인을 제외한 모든 가상자산을 통칭하는 말입니다. 이더리움처럼 큰 코인부터 신생 코인까지 포함되며, 비트코인보다 변동성이 큰 편입니다.", o),
                o -> term("블록체인",
                        "거래 기록을 여러 참여자가 나눠 보관하는 분산 장부 기술입니다. 한번 기록되면 위·변조가 매우 어려워 가상자산의 신뢰 기반이 됩니다.", o),
                o -> term("이더리움(ETH)",
                        "스마트 컨트랙트(자동 실행 계약)를 올릴 수 있는 대표적인 블록체인 플랫폼이자 그 코인입니다. 수많은 알트코인과 디파이·NFT의 기반이 됩니다.", o),
                o -> term("스테이블코인",
                        "달러 같은 법정화폐 가치에 1:1로 연동되도록 설계한 코인입니다. 가격 변동이 거의 없어 거래의 기준 화폐나 잠시 피신처로 쓰입니다.", o),
                o -> term("지갑(월렛)",
                        "가상자산을 보관·송수신하는 도구입니다. 거래소가 맡아주는 지갑과, 내가 직접 비밀키를 관리하는 개인 지갑으로 나뉩니다.", o),
                o -> term("시가총액",
                        "코인 가격에 유통량을 곱한 값으로, 그 코인의 전체 시장 규모를 나타냅니다. 코인끼리 크기를 비교할 때 사용합니다.", o),
                o -> term("비트코인 도미넌스",
                        "전체 가상자산 시가총액에서 비트코인이 차지하는 비중입니다. 이 값이 오르내리는 흐름으로 자금이 BTC와 알트코인 중 어디로 쏠리는지 가늠합니다.", o),
                o -> term("반감기(Halving)",
                        "비트코인 채굴 보상이 약 4년마다 절반으로 줄어드는 이벤트입니다. 새로 풀리는 물량이 감소해 시장에서 중요한 변수로 여겨집니다.", o),
                o -> term("변동성",
                        "가격이 오르내리는 정도를 말합니다. 가상자산은 24시간 거래되고 변동성이 매우 커, 손실 위험과 수익 기회가 함께 큽니다.", o),
                o -> term("분산투자",
                        "한 코인에 몰아넣지 않고 여러 자산·시점에 나눠 투자해 위험을 줄이는 전략입니다. '계란을 한 바구니에 담지 말라'는 말로 요약됩니다.", o),
                o -> term("적립식 투자(DCA)",
                        "정해진 주기에 일정 금액을 꾸준히 나눠 사는 방식입니다. 매입 시점이 분산되어 평균 매입 단가가 안정되며, 변동성이 큰 코인 시장에 특히 잘 맞습니다.", o),
                o -> term("복리",
                        "원금뿐 아니라 거기서 생긴 수익에도 다시 수익이 붙는 구조입니다. 투자 기간이 길수록 자산이 눈덩이처럼 불어납니다.", o),
                o -> term("손절매(손절)",
                        "손실이 더 커지기 전에 미리 정해둔 기준에서 보유 자산을 파는 것입니다. 변동성이 큰 가상자산에서 특히 중요한 위험 관리 방법입니다.", o),
                o -> term("거래량",
                        "일정 기간 동안 사고팔린 코인의 양입니다. 시장의 관심도와 매매가 얼마나 활발한지를 보여주며, 급등락 때 함께 확인하는 지표입니다.", o),
                o -> term("디파이(DeFi)",
                        "은행 같은 중개 기관 없이 블록체인 위에서 예치·대출·교환 등 금융 서비스를 이용하는 것입니다. 높은 이자를 내세우지만 그만큼 위험도 큽니다.", o)
        );
        investmentTermRepository.saveAll(terms);
        log.info("[Seed] 투자 용어집 {}건 적재", terms.size());
    }

    private void seedArticles() {
        if (beginnerArticleRepository.count() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<BeginnerArticle> articles = ordered(
                o -> article("가상자산 투자, 무엇부터 시작할까요?",
                        "가상자산은 블록체인 위에서 발행·거래되는 디지털 자산입니다. 비트코인 한 개를 통째로 살 필요 없이 "
                                + "소수점 단위로 원하는 만큼만 사고팔 수 있어, 적은 돈으로도 시작할 수 있습니다.\n\n"
                                + "처음에는 잃어도 생활에 지장이 없는 소액으로 시작하는 것이 좋습니다. 가상자산은 가격 변동이 매우 크기 때문에, "
                                + "부담이 적어야 시세가 흔들려도 차분하게 배워나갈 수 있습니다.\n\n"
                                + "수많은 코인 중 무엇을 살지 막막하다면, 시가총액이 크고 역사가 긴 비트코인·이더리움처럼 "
                                + "시장을 대표하는 자산부터 살펴보세요. 신생 코인일수록 기대가 큰 만큼 위험도 훨씬 큽니다.\n\n"
                                + "무엇보다 단기 시세에 일희일비하기보다, 이 기술과 시장이 시간이 지나며 자리 잡을지에 주목하는 것이 "
                                + "초보 투자자의 가장 좋은 첫걸음입니다.", o, now),
                o -> article("분산투자와 적립식 투자(DCA), 두 가지 원칙",
                        "변동성이 큰 가상자산에서 위험을 줄이는 가장 확실한 방법은 두 가지로 '나누는' 것입니다. 바로 자산을 나누는 "
                                + "분산투자와 시점을 나누는 적립식 투자(DCA)입니다.\n\n"
                                + "분산투자는 한 코인에 모든 돈을 넣지 않고 여러 자산에 나눠 담는 전략입니다. 한 코인이 급락해도 "
                                + "다른 자산이 충격을 흡수해 주기 때문에, '계란을 한 바구니에 담지 말라'는 말로 자주 표현됩니다.\n\n"
                                + "적립식 투자(DCA)는 한 번에 목돈을 넣는 대신, 정해진 주기에 일정 금액을 꾸준히 나눠 사는 방식입니다. "
                                + "쌀 때도 비쌀 때도 기계적으로 사다 보면 평균 매입 단가가 안정되어, 고점에 한꺼번에 사는 위험을 줄일 수 있습니다.\n\n"
                                + "이 두 원칙은 함께 쓸 때 특히 강합니다. 등락이 심한 코인 시장에서 매일·매주 조금씩 나눠 사는 방식은 "
                                + "초보자가 부담 없이 분산과 적립을 동시에 실천할 수 있는 대표적인 방법입니다.", o, now),
                o -> article("잔돈으로 시작하는 투자 습관",
                        "투자를 미루는 가장 흔한 이유는 '목돈이 없어서'입니다. 하지만 투자에서 중요한 것은 시작 금액의 크기가 아니라 "
                                + "꾸준히 이어가는 습관입니다.\n\n"
                                + "티끌은 결제하고 남는 잔돈을 모아 가상자산 매수에 연결합니다. 커피 한 잔, 점심 한 끼처럼 평소 무심코 쓰던 "
                                + "돈의 끝자리가 조금씩 쌓여, 의식하지 않아도 투자가 굴러가게 됩니다. 코인은 소수점 단위로 살 수 있어 잔돈처럼 작은 금액과도 잘 맞습니다.\n\n"
                                + "작은 금액이라도 시간이 더해지면 의미가 달라집니다. 시점을 잘게 나눠 꾸준히 사 모으는 방식은 "
                                + "변동성이 큰 코인 시장에서 평균 매입 단가를 안정시키는 효과가 있어, 일찍 그리고 꾸준히 시작하는 것 자체가 큰 힘이 됩니다.\n\n"
                                + "부담이 작으니 시장이 출렁여도 마음이 덜 흔들리고, 그만큼 투자를 오래 이어가기 쉽습니다. "
                                + "잔돈 투자는 '작게 시작해서 오래 가는' 좋은 투자 습관의 출발점입니다.", o, now),
                o -> article("변동성 큰 코인 시장, 위험을 다루는 법",
                        "가상자산의 가장 큰 특징은 변동성입니다. 24시간 쉬지 않고 거래되며 하루에도 가격이 크게 오르내리기 때문에, "
                                + "수익 기회만큼 손실 위험도 큽니다. 그래서 초보자에게는 '얼마를 버느냐'보다 '어떻게 잃지 않느냐'가 더 중요합니다.\n\n"
                                + "첫째, 잃어도 괜찮은 돈으로만 투자하세요. 생활비나 빌린 돈으로 투자하면 시세가 급락할 때 합리적인 판단이 어렵습니다.\n\n"
                                + "둘째, 빚으로 베팅을 키우는 레버리지(증거금) 거래는 멀리하세요. 수익이 커지는 만큼 작은 하락에도 원금이 한순간에 "
                                + "사라질 수 있어, 초보자에게는 가장 위험한 함정입니다.\n\n"
                                + "셋째, '지금 안 사면 손해'라는 조급함(FOMO)과 급등 소식에 휩쓸리지 마세요. 미리 정한 원칙대로 나눠 사고, "
                                + "필요하면 손절 기준을 정해 두는 것이 변동성 큰 시장에서 오래 살아남는 길입니다.", o, now)
        );
        beginnerArticleRepository.saveAll(articles);
        log.info("[Seed] 초보자 글 {}건 적재", articles.size());
    }

    private void seedVideos() {
        if (recommendedVideoRepository.count() > 0) {
            return;
        }
        // 직접 큐레이션한 유튜브 기초 강의 3개. 썸네일은 영상 ID 기반 YouTube 이미지 URL.
        List<RecommendedVideo> videos = ordered(
                o -> video("모르면 평생 후회하는 비트코인 쌩기초 개념 총정리 (홍진경, 짱쉬움)",
                        "https://www.youtube.com/watch?v=bvAOzteo3Uk",
                        "https://i.ytimg.com/vi/bvAOzteo3Uk/hqdefault.jpg",
                        "공부왕찐천재 홍진경", o),
                o -> video("초등학생도 이해하는 비트코인 원리",
                        "https://www.youtube.com/watch?v=5dkaMkcTgNA",
                        "https://i.ytimg.com/vi/5dkaMkcTgNA/hqdefault.jpg",
                        "마크의 지식서재", o),
                o -> video("이제는 아셔야 합니다. 상위 '1%'를 위한 암호화폐 최종 요약본, 개념부터 원리까지 완벽 정리!",
                        "https://www.youtube.com/watch?v=jGGbTVOfFFA",
                        "https://i.ytimg.com/vi/jGGbTVOfFFA/hqdefault.jpg",
                        "허성범 Horang", o)
        );
        recommendedVideoRepository.saveAll(videos);
        log.info("[Seed] 추천 영상 {}건 적재", videos.size());
    }

    /** 리스트 위치(1-based)를 display order로 부여한다. 항목을 추가·삭제·재정렬해도 순번을 수동으로 고칠 필요가 없다. */
    @SafeVarargs
    private <T> List<T> ordered(IntFunction<T>... factories) {
        return IntStream.range(0, factories.length)
                .mapToObj(i -> factories[i].apply(i + 1))
                .toList();
    }

    private InvestmentTerm term(String name, String description, int order) {
        return InvestmentTerm.builder().term(name).description(description).displayOrder(order).build();
    }

    private BeginnerArticle article(String title, String body, int order, LocalDateTime publishedAt) {
        return BeginnerArticle.builder()
                .title(title).body(body).displayOrder(order).publishedAt(publishedAt).build();
    }

    private RecommendedVideo video(String title, String videoUrl, String thumbnailUrl, String channelName, int order) {
        return RecommendedVideo.builder()
                .title(title).videoUrl(videoUrl).thumbnailUrl(thumbnailUrl)
                .channelName(channelName).displayOrder(order).build();
    }
}