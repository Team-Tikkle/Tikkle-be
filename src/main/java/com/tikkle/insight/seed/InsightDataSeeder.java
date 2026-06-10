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
        List<InvestmentTerm> terms = List.of(
                term("주식",
                        "기업이 사업 자금을 모으기 위해 발행하는, 회사 소유권을 잘게 나눈 증서입니다. 주식을 사면 그 기업의 주주가 되어 회사의 일부를 소유하게 됩니다.",
                        1),
                term("코스피(KOSPI)",
                        "한국 유가증권시장에 상장된 대표 기업들의 주가를 종합한 지수입니다. 대형 우량주 중심이라 한국 증시 전체의 흐름을 보는 기준이 됩니다.",
                        2),
                term("코스닥(KOSDAQ)",
                        "기술·벤처·중소기업 중심의 주식시장과 그 지수입니다. 코스피보다 성장 기대가 큰 대신 가격 변동성도 큰 편입니다.",
                        3),
                term("ETF",
                        "여러 종목이나 자산을 하나로 묶어 거래소에 상장한 펀드입니다. 한 주만 사도 자동으로 분산투자가 되고, 주식처럼 실시간으로 사고팔 수 있습니다.",
                        4),
                term("배당",
                        "기업이 벌어들인 이익의 일부를 주주에게 나눠주는 것입니다. 보통 현금으로 지급하는 현금배당이 가장 일반적입니다.",
                        5),
                term("시가총액",
                        "주가에 발행 주식 수를 곱한 값으로, 기업의 전체 시장 가치를 나타냅니다. 기업의 규모를 서로 비교할 때 사용합니다.",
                        6),
                term("PER(주가수익비율)",
                        "주가를 주당순이익으로 나눈 값입니다. 기업이 버는 이익에 비해 주가가 비싼지 싼지를 가늠하며, 낮을수록 이익 대비 저평가로 봅니다.",
                        7),
                term("PBR(주가순자산비율)",
                        "주가를 주당순자산으로 나눈 값입니다. 회사가 가진 자산 대비 주가 수준을 보며, 1배이면 주가와 장부상 순자산이 같다는 뜻입니다.",
                        8),
                term("ROE(자기자본이익률)",
                        "기업이 자기 돈(자본)으로 1년 동안 얼마를 벌었는지 보여주는 수익성 지표입니다. 높을수록 자본을 효율적으로 굴렸다는 의미입니다.",
                        9),
                term("분산투자",
                        "한 종목에 몰아넣지 않고 여러 종목·자산·시점에 나눠 투자해 위험을 줄이는 전략입니다. '계란을 한 바구니에 담지 말라'는 말로 요약됩니다.",
                        10),
                term("적립식 투자",
                        "정해진 주기에 일정 금액을 꾸준히 나눠 투자하는 방식입니다. 매입 시점이 분산되어 평균 매입 단가가 안정되는 효과가 있습니다.",
                        11),
                term("복리",
                        "원금뿐 아니라 거기서 생긴 수익에도 다시 수익이 붙는 구조입니다. 투자 기간이 길수록 자산이 눈덩이처럼 불어납니다.",
                        12),
                term("변동성",
                        "가격이 오르내리는 정도를 말합니다. 변동성이 크면 손실 위험과 수익 기회가 함께 커집니다.",
                        13),
                term("손절매(손절)",
                        "손실이 더 커지기 전에 미리 정해둔 기준에서 보유 자산을 파는 것입니다. 큰 손실을 막기 위한 위험 관리 방법입니다.",
                        14),
                term("공모주(IPO)",
                        "기업이 처음으로 증시에 상장하면서 일반 투자자에게 주식을 새로 공개·배정하는 것입니다. 청약을 통해 참여할 수 있습니다.",
                        15),
                term("우선주",
                        "의결권은 없지만 배당 등에서 보통주보다 우선권을 갖는 주식입니다. 종목명 뒤에 '우'가 붙는 경우가 많습니다.",
                        16),
                term("거래량",
                        "일정 기간 동안 매매가 이루어진 주식 수입니다. 시장의 관심도와 매매가 얼마나 활발한지를 보여줍니다.",
                        17),
                term("호가",
                        "주식을 사거나 팔려고 제시하는 가격입니다. 사려는 가격(매수호가)과 팔려는 가격(매도호가)이 맞으면 거래가 체결됩니다.",
                        18)
        );
        investmentTermRepository.saveAll(terms);
        log.info("[Seed] 투자 용어집 {}건 적재", terms.size());
    }

    private void seedArticles() {
        if (beginnerArticleRepository.count() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<BeginnerArticle> articles = List.of(
                article("주식 투자, 무엇부터 시작할까요?",
                        "주식 투자는 기업의 일부를 소유하는 일입니다. 한 주를 사면 그 회사의 작은 주주가 되고, "
                                + "회사가 성장하면 주가나 배당으로 그 과실을 함께 나눠 받게 됩니다.\n\n"
                                + "처음에는 잃어도 생활에 지장이 없는 소액으로 시작하는 것이 좋습니다. 투자는 한 번의 큰 결정이 아니라 "
                                + "오래 이어가는 습관에 가깝기 때문에, 부담이 적어야 꾸준히 배워나갈 수 있습니다.\n\n"
                                + "종목을 고를 때는 내가 잘 아는 기업, 평소 제품이나 서비스를 써본 기업부터 살펴보세요. "
                                + "사업 내용이 이해되는 회사일수록 가격이 흔들릴 때도 차분하게 판단할 수 있습니다.\n\n"
                                + "무엇보다 단기 시세에 일희일비하기보다, 그 기업이 시간이 지나며 더 성장할 수 있을지에 주목하는 것이 "
                                + "초보 투자자의 가장 좋은 첫걸음입니다.",
                        1, now),
                article("분산투자와 적립식 투자, 두 가지 원칙",
                        "초보자가 위험을 줄이는 가장 확실한 방법은 두 가지로 '나누는' 것입니다. 바로 종목을 나누는 분산투자와 "
                                + "시점을 나누는 적립식 투자입니다.\n\n"
                                + "분산투자는 한 종목에 모든 돈을 넣지 않고 여러 종목·자산에 나눠 담는 전략입니다. 한 기업이 부진해도 "
                                + "다른 자산이 충격을 흡수해 주기 때문에, '계란을 한 바구니에 담지 말라'는 말로 자주 표현됩니다.\n\n"
                                + "적립식 투자는 한 번에 목돈을 넣는 대신, 정해진 주기에 일정 금액을 꾸준히 나눠 사는 방식입니다. "
                                + "쌀 때도 비쌀 때도 기계적으로 사다 보면 평균 매입 단가가 안정되어, 고점에 한꺼번에 사는 위험을 줄일 수 있습니다.\n\n"
                                + "이 두 원칙은 함께 쓸 때 특히 강합니다. 여러 종목을 담은 ETF를 매달 조금씩 적립하는 방식은 "
                                + "초보자가 부담 없이 분산과 적립을 동시에 실천할 수 있는 대표적인 방법입니다.",
                        2, now),
                article("잔돈으로 시작하는 투자 습관",
                        "투자를 미루는 가장 흔한 이유는 '목돈이 없어서'입니다. 하지만 투자에서 중요한 것은 시작 금액의 크기가 아니라 "
                                + "꾸준히 이어가는 습관입니다.\n\n"
                                + "티끌은 결제하고 남는 잔돈을 모아 자동으로 투자에 연결합니다. 커피 한 잔, 점심 한 끼처럼 평소 무심코 쓰던 "
                                + "돈의 끝자리가 조금씩 쌓여, 의식하지 않아도 투자가 굴러가게 됩니다.\n\n"
                                + "작은 금액이라도 시간이 더해지면 의미가 달라집니다. 원금에서 생긴 수익에 다시 수익이 붙는 복리 효과는 "
                                + "기간이 길수록 커지기 때문에, 일찍 그리고 꾸준히 시작하는 것 자체가 큰 힘이 됩니다.\n\n"
                                + "부담이 작으니 시장이 출렁여도 마음이 덜 흔들리고, 그만큼 투자를 오래 이어가기 쉽습니다. "
                                + "잔돈 투자는 '작게 시작해서 오래 가는' 좋은 투자 습관의 출발점입니다.",
                        3, now),
                article("ETF로 쉽게 분산투자하기",
                        "ETF는 여러 종목이나 자산을 하나로 묶어 거래소에 상장한 펀드입니다. 단 한 주만 사도 그 안에 담긴 "
                                + "수십~수백 개 종목에 자동으로 나눠 투자하는 효과가 생깁니다.\n\n"
                                + "예를 들어 코스피200을 따라가는 ETF를 사면, 한국을 대표하는 200개 기업에 한 번에 분산투자하는 셈입니다. "
                                + "개별 종목을 일일이 고르고 관리하기 어려운 초보자에게 특히 잘 맞는 방식입니다.\n\n"
                                + "ETF는 주식처럼 시장이 열린 시간에 실시간으로 사고팔 수 있고, 일반 펀드보다 운용 보수가 낮은 편이라 "
                                + "장기·적립식 투자와 잘 어울립니다.\n\n"
                                + "다만 ETF도 결국 시장에 투자하는 상품이라 가격은 오르내립니다. 분산으로 위험을 '줄이는' 것이지 "
                                + "'없애는' 것은 아니라는 점을 기억하고, 무엇을 담고 있는 ETF인지 확인한 뒤 시작하세요.",
                        4, now)
        );
        beginnerArticleRepository.saveAll(articles);
        log.info("[Seed] 초보자 글 {}건 적재", articles.size());
    }

    private void seedVideos() {
        if (recommendedVideoRepository.count() > 0) {
            return;
        }
        // 직접 큐레이션한 유튜브 기초 강의 3개. 썸네일은 영상 ID 기반 YouTube 이미지 URL.
        List<RecommendedVideo> videos = List.of(
                video("가장 기본적인 투자방법",
                        "https://www.youtube.com/watch?v=5lHHAxyXI_g",
                        "https://i.ytimg.com/vi/5lHHAxyXI_g/hqdefault.jpg",
                        "머니코믹스 Money Comics", 1),
                video("2시간만에 주린이 탈출...주식투자 기초 완전 마스터 (18만원 상당의 선물까지!)",
                        "https://www.youtube.com/watch?v=BfxO1AZ1Xek",
                        "https://i.ytimg.com/vi/BfxO1AZ1Xek/hqdefault.jpg",
                        "와이스트릿 - 지식과 자산의 복리효과", 2),
                video("초등학생도 이해하는 주식투자 기초 개념",
                        "https://www.youtube.com/watch?v=nqbKOvQ8x1s",
                        "https://i.ytimg.com/vi/nqbKOvQ8x1s/hqdefault.jpg",
                        "마크의 지식서재", 3)
        );
        recommendedVideoRepository.saveAll(videos);
        log.info("[Seed] 추천 영상 {}건 적재", videos.size());
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