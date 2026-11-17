package com.tikkle.payment.interceptor;

import com.tikkle.support.PaymentSignatures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignatureValidatorTest {

    private static final String SECRET_KEY = "test-only-payment-signature-key";
    private static final String PAYLOAD = "{\"userId\":1,\"amount\":4500}";
    private static final String TIMESTAMP = "1763280000";

    private final SignatureValidator validator = new SignatureValidator(SECRET_KEY);

    @Test
    @DisplayName("본문과 타임스탬프를 같은 키로 서명한 값이면 통과한다")
    void acceptsMatchingSignature() {
        String signature = PaymentSignatures.sign(SECRET_KEY, PAYLOAD, TIMESTAMP);

        assertThat(validator.isValid(PAYLOAD, TIMESTAMP, signature)).isTrue();
    }

    @Test
    @DisplayName("본문이 한 글자라도 바뀌면 실패한다")
    void rejectsTamperedPayload() {
        String signature = PaymentSignatures.sign(SECRET_KEY, PAYLOAD, TIMESTAMP);

        assertThat(validator.isValid(PAYLOAD.replace("4500", "9500"), TIMESTAMP, signature)).isFalse();
    }

    @Test
    @DisplayName("타임스탬프가 서명할 때와 다르면 실패한다")
    void rejectsDifferentTimestamp() {
        String signature = PaymentSignatures.sign(SECRET_KEY, PAYLOAD, TIMESTAMP);

        assertThat(validator.isValid(PAYLOAD, "1763280001", signature)).isFalse();
    }

    @Test
    @DisplayName("다른 키로 서명한 값이면 실패한다")
    void rejectsSignatureFromAnotherKey() {
        String signature = PaymentSignatures.sign("another-key", PAYLOAD, TIMESTAMP);

        assertThat(validator.isValid(PAYLOAD, TIMESTAMP, signature)).isFalse();
    }

    @Test
    @DisplayName("Base64가 아닌 서명은 예외 없이 실패로 처리한다")
    void rejectsMalformedSignature() {
        assertThat(validator.isValid(PAYLOAD, TIMESTAMP, "not-base64!!")).isFalse();
        assertThat(validator.isValid(PAYLOAD, TIMESTAMP, "")).isFalse();
    }
}
