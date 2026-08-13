package com.tikkle.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {

    /**
     * 스케줄러 전용 스레드 풀을 구성합니다.
     * 기본값은 단일 스레드라 오래 걸리는 작업 하나가 나머지 스케줄러를 전부 막습니다.
     * 특히 3초 주기 입금 폴링이 밀리면 실제로 도착한 입금을 타임아웃으로 오판하게 되므로
     * 결제 폴링이 다른 작업에 막히지 않도록 여유를 둡니다.
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("tikkle-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
