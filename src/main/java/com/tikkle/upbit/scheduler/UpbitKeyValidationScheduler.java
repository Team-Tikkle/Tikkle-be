package com.tikkle.upbit.scheduler;

import com.tikkle.upbit.exception.UpbitInvalidKeyException;
import com.tikkle.upbit.service.UpbitKeyValidationService;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.repository.LinkedAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 매일 새벽 업비트 API를 호출하여 만료된 유저의 API 키를 검증하는 백그라운드 스케줄러입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpbitKeyValidationScheduler {

    private final LinkedAccountRepository linkedAccountRepository;
    private final UpbitKeyValidationService upbitKeyValidationService;

    // 매일 새벽 3시에 실행
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void validateAllUpbitKeys() {
        log.info("[UpbitKeyValidationScheduler] 전체 활성 유저 업비트 키 유효성 점검 시작");

        List<LinkedAccount> activeAccounts = linkedAccountRepository.findByIsUpbitKeyValidTrue();

        int successCount = 0;
        int failCount = 0;

        for (LinkedAccount account : activeAccounts) {
            try {
                upbitKeyValidationService.validateKeyOrThrow(
                        account.getUpbitAccessKey(),
                        account.getUpbitSecretKey()
                );
                successCount++;
            } catch (UpbitInvalidKeyException e) {
                log.warn("[UpbitKeyValidationScheduler] 권한 만료 감지 - userId: {}", account.getUser().getId());
                account.invalidateUpbitKey();
                
                failCount++;
            } catch (Exception e) {
                log.error("[UpbitKeyValidationScheduler] 검증 중 알 수 없는 에러 발생 - userId: {}", account.getUser().getId(), e);
            }
        }

        log.info("[UpbitKeyValidationScheduler] 전체 활성 유저 업비트 키 유효성 점검 완료 (성공: {}, 무효화: {})", successCount, failCount);
    }
}