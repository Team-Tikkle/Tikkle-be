package com.tikkle.payment.filter;

import com.tikkle.payment.filter.RequestBodyCachingFilter.CachedBodyHttpServletRequest;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제 수신 요청의 본문 캐싱 필터가 본문 크기를 제한하는지 확인합니다.
 * 스프링 컨텍스트 없이 필터만 실행합니다.
 */
class RequestBodyCachingFilterTest {

    private static final String PAYMENT_PATH = "/api/payments";
    private static final int MAX_BODY_BYTES = 8 * 1024;

    private final RequestBodyCachingFilter filter = new RequestBodyCachingFilter(JsonMapper.builder().build());
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @Test
    @DisplayName("길이를 알리지 않은 본문이 8KB를 넘으면 413(COMMON-005)으로 끝내고 다음 단계로 넘기지 않는다")
    void rejectsOversizedBodyWithoutContentLength() throws Exception {
        MockHttpServletRequest request = paymentRequestWithoutContentLength(new byte[MAX_BODY_BYTES + 1]);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("COMMON-005");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("길이를 알리지 않은 본문이 꼭 8KB이면 본문을 담아 다음 단계로 넘긴다")
    void passesBodyAtLimitWithoutContentLength() throws Exception {
        byte[] body = "x".repeat(MAX_BODY_BYTES).getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = paymentRequestWithoutContentLength(body);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isInstanceOf(CachedBodyHttpServletRequest.class);
        assertThat(((CachedBodyHttpServletRequest) chain.getRequest()).getCachedBody()).isEqualTo(body);
    }

    @Test
    @DisplayName("알려 온 길이가 8KB를 넘으면 본문을 읽지 않고 413으로 끝낸다")
    void rejectsByContentLengthWithoutReadingBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PAYMENT_PATH) {
            @Override
            public long getContentLengthLong() {
                return MAX_BODY_BYTES + 1L;
            }

            @Override
            public ServletInputStream getInputStream() {
                throw new AssertionError("본문을 읽으면 안 된다");
            }
        };
        request.setServletPath(PAYMENT_PATH);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("결제 수신이 아닌 요청은 크기와 무관하게 그대로 넘긴다")
    void ignoresOtherRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setServletPath("/api/auth/login");
        request.setContent(new byte[MAX_BODY_BYTES + 1]);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    // 길이를 알리지 않고 보내는 요청(chunked)처럼 Content-Length 가 없는 결제 수신 요청을 만든다
    private MockHttpServletRequest paymentRequestWithoutContentLength(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PAYMENT_PATH) {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setServletPath(PAYMENT_PATH);
        request.setContent(body);
        return request;
    }
}
