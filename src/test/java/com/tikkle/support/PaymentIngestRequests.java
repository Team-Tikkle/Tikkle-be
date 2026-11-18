package com.tikkle.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 결제 수신 API(POST /api/payments)에 보낼 요청을 테스트에서 만들 때 씁니다.
 * 본문을 만들고, 앱과 같은 방식으로 서명해 헤더에 넣습니다.
 */
@TestComponent
public class PaymentIngestRequests {

    public static final String PATH = "/api/payments";
    public static final String SIGNATURE_HEADER = "X-Tikkle-Signature";
    public static final String TIMESTAMP_HEADER = "X-Tikkle-Timestamp";

    private final String secretKey;
    private final JsonMapper jsonMapper;

    public PaymentIngestRequests(@Value("${tikkle.payment.secret-key}") String secretKey, JsonMapper jsonMapper) {
        this.secretKey = secretKey;
        this.jsonMapper = jsonMapper;
    }

    public String body(Long userId, String cardCompany, String cardNumberLast4,
                       String merchant, int amount, String transactionId) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("userId", userId);
        fields.put("cardCompany", cardCompany);
        fields.put("cardNumberLast4", cardNumberLast4);
        fields.put("merchant", merchant);
        fields.put("amount", amount);
        fields.put("transactionId", transactionId);
        return jsonMapper.writeValueAsString(fields);
    }

    /**
     * 지금 시각의 타임스탬프로 서명한 결제 수신 요청을 만듭니다.
     */
    public MockHttpServletRequestBuilder signed(String body) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        // 본문 캐싱 필터는 서블릿 경로로 결제 수신 요청을 가려낸다. 운영(Tomcat)에서는 서블릿 경로가 요청 경로와 같지만
        // MockMvc 는 비워 두므로 같은 값을 넣어 운영과 같은 필터 경로를 타게 한다
        return post(PATH)
                .servletPath(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header(TIMESTAMP_HEADER, timestamp)
                .header(SIGNATURE_HEADER, PaymentSignatures.sign(secretKey, body, timestamp));
    }
}
