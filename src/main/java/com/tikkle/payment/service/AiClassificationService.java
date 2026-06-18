package com.tikkle.payment.service;

import com.tikkle.payment.dto.response.AiClassificationResponse;
import com.tikkle.payment.exception.InvalidAiResponseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AiClassificationService {
    private final ChatClient chatClient;

    public AiClassificationService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        당신은 결제 가맹점 카테고리 분류 AI입니다.
                        사용자가 가맹점 이름을 입력하면, 가맹점의 핵심 상호명을 'keyword'로 추출하세요.
                        그리고 다음 7가지 카테고리 기준을 참고하여 가장 적합한 하나를 'category'로 분류하세요.
                        
                        [카테고리 목록 및 분류 기준]
                        - CAFE: 카페, 디저트, 베이커리
                        - MART: 편의점, 대형마트, 동네 마트
                        - FOOD: 식당, 음식점, 패스트푸드
                        - SHOPPING: 쇼핑, 의류, 잡화, 백화점
                        - TRAFFIC: 교통, 주유, 대중교통, 택시
                        - CULTURE: 문화, 여가, 서점, 영화, 전시
                        - ETC: 위 카테고리에 명확히 속하지 않는 기타 결제 건
                        """)
                .build();
    }

    public AiClassificationResponse classify(String merchant) {
        log.info("AI 분류 요청: {}", merchant);

        AiClassificationResponse response = chatClient.prompt()
                .user(merchant)
                .call()
                .entity(AiClassificationResponse.class);

        log.info("AI 분류 응답: {}", response);

        if (response == null || response.keyword() == null || response.category() == null) {
            throw new InvalidAiResponseException();
        }

        return response;
    }
}
