package com.tikkle.support;

import com.tikkle.global.security.jwt.JwtProvider;
import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.TwoFactorProvider;
import com.tikkle.user.repository.LinkedAccountRepository;
import com.tikkle.user.repository.UserRepository;
import org.springframework.boot.test.context.TestComponent;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 테스트에 필요한 사용자·연동 계정·결제 이벤트를 만들어 저장하고, 인증 헤더를 발급합니다.
 * 값은 전부 테스트용 가짜 값입니다.
 */
@TestComponent
public class TestDataFactory {

    public static final String UPBIT_ACCESS_KEY = "test-only-upbit-access-key";
    public static final String UPBIT_SECRET_KEY = "test-only-upbit-secret-key-0123456789";

    private final UserRepository userRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final JwtProvider jwtProvider;

    // 전화번호는 UNIQUE 라서 테스트마다 겹치지 않게 번호를 올린다
    private final AtomicInteger sequence = new AtomicInteger();

    public TestDataFactory(UserRepository userRepository,
                           LinkedAccountRepository linkedAccountRepository,
                           PaymentEventRepository paymentEventRepository,
                           JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.linkedAccountRepository = linkedAccountRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.jwtProvider = jwtProvider;
    }

    public User createUser() {
        int number = sequence.incrementAndGet();
        return userRepository.save(User.builder()
                .name("테스트" + number)
                .phoneNumber(String.format("010%08d", number))
                .password("encoded-password")
                .build());
    }

    /**
     * 업비트 키와 대상 카드가 등록된 연동 계정을 만듭니다. 2차 인증 수단은 null 을 줄 수 있습니다.
     */
    public LinkedAccount createLinkedAccount(User user, TwoFactorProvider twoFactorProvider) {
        return linkedAccountRepository.save(LinkedAccount.builder()
                .user(user)
                .upbitAccessKey(UPBIT_ACCESS_KEY)
                .upbitSecretKey(UPBIT_SECRET_KEY)
                .targetCardCompany("KBANK")
                .targetCardLast4("1234")
                .twoFactorProvider(twoFactorProvider)
                .build());
    }

    /**
     * 잔돈 5,500원이 계산된 결제 이벤트를 주어진 상태로 만듭니다.
     */
    public PaymentEvent createPaymentEvent(Long userId, PaymentStatus status) {
        return paymentEventRepository.save(PaymentEvent.builder()
                .userId(userId)
                .cardCompany("KBANK")
                .cardNumberLast4("1234")
                .merchant("스타벅스")
                .rawMerchant("스타벅스 강남점")
                .amount(4500)
                .spareChange(5500)
                .category(PaymentCategory.CAFE)
                .transactionId("tx-" + UUID.randomUUID())
                .status(status)
                .build());
    }

    public String bearerToken(Long userId) {
        return "Bearer " + jwtProvider.createAccessToken(userId);
    }
}
