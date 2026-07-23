# Tikkle DB 스키마 (DDL)

> 이 파일을 참고하여 API 설계, 쿼리 작성, 기능 개발 시 테이블 구조를 파악하세요.
> **본 문서는 `src/main/java/com/tikkle` 엔티티 코드 기준으로 동기화되어 있습니다.**
> 테이블 물리명은 전부 **대문자 스네이크 케이스**입니다 (AGENTS.md §5.4 규칙).

---

## 테이블 목록

| 테이블명 | 설명 |
|---------|------|
| `USERS` | 회원 기본 정보 (휴대폰 번호 + 비밀번호 인증) |
| `INVESTMENT_PROFILE` | 유저별 가상자산 투자 성향(5축) |
| `INVESTMENT_PROFILE_THEMES` | 투자 프로필별 관심 코인 테마 (다중 선택, Set) |
| `CATEGORY_SPARE_CHANGE_RULES` | 카테고리별 다이나믹 잔돈 규칙 |
| `LINKED_ACCOUNTS` | 금융 연동 정보 (Upbit API 키, 타겟 카드, 2차 인증 수단) |
| `PAYMENT_EVENTS` | 결제 이벤트 원장 |
| `PAYMENT_CATEGORY_MAPPING` | 가맹점 키워드 → 카테고리 분류 사전 (전역 캐시, AI 학습 결과 누적) |
| `AI_RECOMMENDATION_HISTORY` | 12시간 주기 AI 추천 15종목 유니버스 이력 (Risk x Trend 9개 조합별 캐싱용) |
| `PORTFOLIOS` | 보유 종목 현황 |
| `COIN_METADATA` | 업비트 마켓(코인) 메타데이터 (마켓코드/한글명/영문명, 매일 동기화) |
| `MARKET_TOPICS` | 투데이 마켓 토픽 (Google News RSS 수집) |
| `INVESTMENT_TERMS` | 투자 용어집 (시딩) |
| `BEGINNER_ARTICLES` | 초보자 가이드 글 (시딩, 앱 내부 렌더링) |
| `RECOMMENDED_VIDEOS` | 추천 영상 (시딩, 외부 링크) |
| `DEVICE_TOKENS` | FCM 디바이스 토큰 (결과 알림 발송 대상, 유저당 다중 기기) |

> **Redis 저장소 (RDB 외)**
>
> | 키 | 용도 | TTL |
> |---|---|---|
> | `refresh_token` (RedisHash) | 리프레시 토큰 | 토큰 만료시간 |
> | `payment:tx:{transactionId}` | 결제 멱등성 키 (SETNX) | 24시간 |
> | `user:settings:{userId}` (Hash) | 유저 설정 캐시 (투자 on/off, 타겟 카드, 카테고리별 규칙) | 무기한 (설정 변경 시 갱신, 탈퇴 시 삭제) |
> | `insight:market-topics` | 마켓 토픽 목록 캐시 | 12시간 (수집 배치 완료 시 무효화) |
> | `ai:candidates:{profileHashKey}` | AI 추천 유니버스 후보군 (예: `BUY_MORE:FULL_TREND`) | 12시간 |
> | `SMS_AUTH:{purpose}:{phone}` | SMS 인증번호 | 3분 |
> | `SMS_ATTEMPT:{purpose}:{phone}` | 인증번호 검증 실패 횟수 (5회 초과 시 무효화) | 인증번호와 동일 주기 |
> | `SMS_COOLDOWN:{purpose}:{phone}` | 재발송 쿨다운 | 60초 |
> | `SMS_DAILY:{purpose}:{phone}` | 일일 발송 횟수 (5회 한도) | 24시간 |
> | `SIGNUP_TOKEN:{phone}` / `PASSWORD_RESET_TOKEN:{phone}` | SMS 인증 완료 토큰 | 30분 |
>
> `{purpose}`는 `SmsPurpose` enum (`SIGNUP`, `PASSWORD_RESET`). 스케줄러용 분산 락은 존재하지 않으므로 **단일 인스턴스 운영을 전제**한다.

---

## DDL

```sql
CREATE TABLE `USERS` (
    `id`            BIGINT          NOT NULL    AUTO_INCREMENT,
    `name`          VARCHAR(50)     NOT NULL,
    `phone_number`  VARCHAR(20)     NOT NULL,   -- 로그인 식별자 (UNIQUE)
    `password`      VARCHAR(255)    NOT NULL,   -- BCrypt 해시
    `created_at`    DATETIME        NOT NULL
);
-- 회원 탈퇴는 논리 삭제가 아닌 완전 삭제다. USERS 행과 소유 데이터(INVESTMENT_PROFILE(+THEMES),
-- CATEGORY_SPARE_CHANGE_RULES, LINKED_ACCOUNTS, PORTFOLIOS, PAYMENT_EVENTS) 및 Redis 세션·설정 캐시를
-- 모두 제거하며, phone_number가 즉시 회수되어 동일 번호로 곧바로 재가입할 수 있다.
-- 따라서 계정 상태 개념 자체가 없다.

CREATE TABLE `PAYMENT_EVENTS` (
    `id`                    BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`              BIGINT          NOT NULL,   -- FK 매핑 없이 Long 스칼라로 보관(느슨한 결합)
    `card_company`          VARCHAR(50)     NOT NULL,
    `card_number_last_4`    VARCHAR(4)      NOT NULL,
    `merchant`              VARCHAR(100)    NOT NULL,   -- 분류된 keyword 또는 원본 가맹점명
    `raw_merchant`          VARCHAR(100)    NOT NULL,   -- 스크래핑된 원본 가맹점명
    `amount`                INT             NOT NULL,
    `spare_change`          INT             NOT NULL,
    `category`              VARCHAR(20)     NULL,       -- PaymentCategory
    `status`                VARCHAR(20)     NOT NULL,   -- PaymentStatus
    `transaction_id`        VARCHAR(255)    NOT NULL,   -- 결정적 해시 기반 고유 ID (UNIQUE)
    `reason`                VARCHAR(255)    NULL,       -- 미투자/실패 사유 등
    `created_at`            DATETIME        NOT NULL,
    `deposit_uuid`          VARCHAR(255)    NULL,       -- 업비트 원화 입금 요청 UUID (UNIQUE)
    `deposit_requested_at`  DATETIME        NULL,       -- 입금 요청 시각 (폴링 타임아웃 기준)
    `trade_uuid`            VARCHAR(255)    NULL,       -- 업비트 매수 주문 UUID (UNIQUE)
    `trade_requested_at`    DATETIME        NULL,       -- 매수 주문 시각 (폴링 타임아웃 기준)
    `target_coin_market`    VARCHAR(20)     NULL,       -- FK -> COIN_METADATA(market)
    `invested_volume`       DECIMAL(30,8)   NULL,       -- 체결 수량
    `invested_price`        DECIMAL(30,8)   NULL        -- 체결 평균 단가
);

CREATE TABLE `INVESTMENT_PROFILE` (
    `id`                    BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`               BIGINT          NOT NULL,
    `risk_tolerance`        VARCHAR(30)     NULL,       -- RiskTolerance (하락장 방어 심리)
    `trend_sensitivity`     VARCHAR(30)     NULL,       -- TrendSensitivity (트렌드 민감도)
    `diversification_type`  VARCHAR(30)     NULL,       -- DiversificationType (포트폴리오 분산도)
    `meme_acceptance`       VARCHAR(30)     NULL        -- MemeAcceptance (밈 코인 수용도)
);
-- 가입 시 빈 행이 먼저 생성되고 온보딩(설정) 시점에 채워지므로 4축(risk_tolerance, trend_sensitivity, diversification_type, meme_acceptance) 컬럼은 nullable. 5번째 축(crypto_themes)은 별도 INVESTMENT_PROFILE_THEMES 테이블에 저장된다.

CREATE TABLE `INVESTMENT_PROFILE_THEMES` (
    `investment_profile_id` BIGINT          NOT NULL,
    `theme`                 VARCHAR(30)     NOT NULL    -- CryptoTheme (관심 코인 테마, 다중 / 최대 6개)
);

CREATE TABLE `CATEGORY_SPARE_CHANGE_RULES` (
    `id`            BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`       BIGINT          NOT NULL,
    `category`      VARCHAR(30)     NOT NULL,   -- PaymentCategory
    `rule_type`     VARCHAR(30)     NOT NULL    -- RuleType
);

CREATE TABLE `LINKED_ACCOUNTS` (
    `id`                        BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`                   BIGINT          NOT NULL,
    `upbit_access_key`          VARCHAR(512)    NULL,       -- AES-256 암호화 저장. 가입 시 빈 행 생성 후 연동 시점에 채움
    `upbit_secret_key`          VARCHAR(512)    NULL,       -- AES-256 암호화 저장. 가입 시 빈 행 생성 후 연동 시점에 채움
    `target_card_company`       VARCHAR(50)     NULL,
    `target_card_last4`         VARCHAR(4)      NULL,       -- 엔티티에 @Column(name=...)이 없어 Hibernate 기본 전략이 생성한 이름 (PAYMENT_EVENTS의 card_number_last_4와 표기가 다름에 주의)
    `two_factor_provider`       VARCHAR(20)     NULL,       -- TwoFactorProvider (업비트 원화 입금 2차 인증 수단)
    `is_investment_enabled`     BOOLEAN         NOT NULL    DEFAULT TRUE
);
-- 업비트 키의 유효성은 별도 컬럼으로 보관하지 않는다.
-- 등록/수정 시점에만 UpbitKeyValidationService가 5대 권한을 검증하고,
-- 이후의 만료/권한 회수는 실제 업비트 API 호출이 401을 반환할 때 감지되어 UPBIT-010으로 전파된다.

CREATE TABLE `PAYMENT_CATEGORY_MAPPING` (
    `id`            BIGINT          NOT NULL    AUTO_INCREMENT,
    `keyword`       VARCHAR(255)    NOT NULL,   -- 가맹점 핵심 상호명 (UNIQUE)
    `category`      VARCHAR(30)     NOT NULL    -- PaymentCategory
);

CREATE TABLE `AI_RECOMMENDATION_HISTORY` (
    `id`                BIGINT          NOT NULL    AUTO_INCREMENT,
    `profile_hash_key`  VARCHAR(255)    NOT NULL,   -- 예: BUY_MORE:FULL_TREND
    `fng_index`         VARCHAR(255)    NOT NULL,
    `btc_dominance`     VARCHAR(255)    NOT NULL,
    `weekly_trend`      VARCHAR(255)    NOT NULL,
    `candidates_json`   TEXT            NOT NULL,   -- 15개 추천 후보군 JSON
    `hot_narratives`    VARCHAR(255)    NULL,       -- 수집된 시장 내러티브 요약
    `macro_events`      TEXT            NULL,       -- 수집된 매크로 이벤트
    `created_at`        DATETIME        NOT NULL
);

CREATE TABLE `PORTFOLIOS` (
    `id`                BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`           BIGINT          NOT NULL,
    `market`            VARCHAR(20)     NOT NULL,
    `quantity`          DECIMAL(20,8)   NOT NULL,   -- 보유 수량
    `average_price`     DECIMAL(20,4)   NOT NULL,   -- 가중 평균 매수 단가
    `created_at`        DATETIME        NOT NULL
);

CREATE TABLE `COIN_METADATA` (
    `market`        VARCHAR(20)     NOT NULL,   -- PK, 예: KRW-BTC
    `korean_name`   VARCHAR(100)    NOT NULL,
    `english_name`  VARCHAR(100)    NOT NULL,
    `updated_at`    DATETIME        NOT NULL
);

-- 인사이트 관련 테이블 (FK 없는 독립 테이블)
CREATE TABLE `MARKET_TOPICS` (
    `id`                BIGINT          NOT NULL    AUTO_INCREMENT,
    `title`             VARCHAR(300)    NOT NULL,
    `press`             VARCHAR(100)    NULL,
    `link`              VARCHAR(500)    NOT NULL,
    `thumbnail_url`     VARCHAR(500)    NULL,
    `published_at`      DATETIME        NULL,
    `keyword`           VARCHAR(50)     NULL,
    `fetched_at`        DATETIME        NOT NULL
);
CREATE TABLE `INVESTMENT_TERMS` (
    `id`                BIGINT          NOT NULL    AUTO_INCREMENT,
    `term`              VARCHAR(100)    NOT NULL,
    `description`       TEXT            NOT NULL,
    `display_order`     INT             NOT NULL
);
CREATE TABLE `BEGINNER_ARTICLES` (
    `id`                BIGINT          NOT NULL    AUTO_INCREMENT,
    `title`             VARCHAR(200)    NOT NULL,
    `body`              TEXT            NOT NULL,
    `thumbnail_url`     VARCHAR(500)    NULL,
    `display_order`     INT             NOT NULL,
    `published_at`      DATETIME        NULL
);
CREATE TABLE `RECOMMENDED_VIDEOS` (
    `id`                BIGINT          NOT NULL    AUTO_INCREMENT,
    `title`             VARCHAR(200)    NOT NULL,
    `video_url`         VARCHAR(500)    NOT NULL,
    `thumbnail_url`     VARCHAR(500)    NULL,
    `channel_name`      VARCHAR(100)    NULL,
    `display_order`     INT             NOT NULL
);
CREATE TABLE `DEVICE_TOKENS` (
    `id`                BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`           BIGINT          NOT NULL,   -- USERS FK (@ManyToOne). 회원 탈퇴 시 명시적으로 함께 삭제
    `fcm_token`         VARCHAR(512)    NOT NULL,   -- UNIQUE. 재로그인/기기 양도 시 소유 user_id를 이전(upsert)
    `created_at`        DATETIME        NOT NULL,
    `updated_at`        DATETIME        NOT NULL
);

```

---
## CONSTRAINTS

```sql
-- PRIMARY KEY
ALTER TABLE `USERS`                         ADD CONSTRAINT `PK_USERS`                       PRIMARY KEY (`id`);
ALTER TABLE `PAYMENT_EVENTS`                ADD CONSTRAINT `PK_PAYMENT_EVENTS`              PRIMARY KEY (`id`);
ALTER TABLE `INVESTMENT_PROFILE`            ADD CONSTRAINT `PK_INVESTMENT_PROFILE`          PRIMARY KEY (`id`);
ALTER TABLE `CATEGORY_SPARE_CHANGE_RULES`   ADD CONSTRAINT `PK_CATEGORY_SPARE_CHANGE_RULES` PRIMARY KEY (`id`);
ALTER TABLE `LINKED_ACCOUNTS`               ADD CONSTRAINT `PK_LINKED_ACCOUNTS`             PRIMARY KEY (`id`);
ALTER TABLE `PAYMENT_CATEGORY_MAPPING`      ADD CONSTRAINT `PK_PAYMENT_CATEGORY_MAPPING`    PRIMARY KEY (`id`);
ALTER TABLE `AI_RECOMMENDATION_HISTORY`     ADD CONSTRAINT `PK_AI_RECOMMENDATION_HISTORY`   PRIMARY KEY (`id`);
ALTER TABLE `PORTFOLIOS`                    ADD CONSTRAINT `PK_PORTFOLIOS`                  PRIMARY KEY (`id`);
ALTER TABLE `COIN_METADATA`                 ADD CONSTRAINT `PK_COIN_METADATA`               PRIMARY KEY (`market`);
ALTER TABLE `MARKET_TOPICS`                 ADD CONSTRAINT `PK_MARKET_TOPICS`               PRIMARY KEY (`id`);
ALTER TABLE `INVESTMENT_TERMS`              ADD CONSTRAINT `PK_INVESTMENT_TERMS`            PRIMARY KEY (`id`);
ALTER TABLE `BEGINNER_ARTICLES`             ADD CONSTRAINT `PK_BEGINNER_ARTICLES`           PRIMARY KEY (`id`);
ALTER TABLE `RECOMMENDED_VIDEOS`            ADD CONSTRAINT `PK_RECOMMENDED_VIDEOS`          PRIMARY KEY (`id`);
ALTER TABLE `DEVICE_TOKENS`                 ADD CONSTRAINT `PK_DEVICE_TOKENS`               PRIMARY KEY (`id`);

-- UNIQUE
ALTER TABLE `USERS`                         ADD CONSTRAINT `UQ_USERS_PHONE_NUMBER`          UNIQUE (`phone_number`);
ALTER TABLE `PAYMENT_EVENTS`                ADD CONSTRAINT `UQ_PAYMENT_EVENTS_TX_ID`        UNIQUE (`transaction_id`);
ALTER TABLE `PAYMENT_EVENTS`                ADD CONSTRAINT `UQ_PAYMENT_EVENTS_DEPOSIT_UUID` UNIQUE (`deposit_uuid`);
ALTER TABLE `PAYMENT_EVENTS`                ADD CONSTRAINT `UQ_PAYMENT_EVENTS_TRADE_UUID`   UNIQUE (`trade_uuid`);
ALTER TABLE `INVESTMENT_PROFILE`            ADD CONSTRAINT `UQ_INVESTMENT_PROFILE_USER`     UNIQUE (`user_id`);
ALTER TABLE `CATEGORY_SPARE_CHANGE_RULES`   ADD CONSTRAINT `UQ_CATEGORY_RULES_USER_CAT`     UNIQUE (`user_id`, `category`);
ALTER TABLE `LINKED_ACCOUNTS`               ADD CONSTRAINT `UQ_LINKED_ACCOUNTS_USER`        UNIQUE (`user_id`);
ALTER TABLE `PAYMENT_CATEGORY_MAPPING`      ADD CONSTRAINT `UQ_PAYMENT_CAT_MAPPING_KEYWORD` UNIQUE (`keyword`);
ALTER TABLE `PORTFOLIOS`                    ADD CONSTRAINT `UQ_USER_MARKET`                 UNIQUE (`user_id`, `market`);
ALTER TABLE `MARKET_TOPICS`                 ADD CONSTRAINT `UQ_MARKET_TOPICS_LINK`          UNIQUE (`link`);
ALTER TABLE `DEVICE_TOKENS`                 ADD CONSTRAINT `UQ_DEVICE_TOKENS_FCM_TOKEN`     UNIQUE (`fcm_token`);

-- FOREIGN KEY (JPA 연관관계)
ALTER TABLE `INVESTMENT_PROFILE`            ADD CONSTRAINT `FK_INVESTMENT_PROFILE_USER`     FOREIGN KEY (`user_id`) REFERENCES `USERS`(`id`);
ALTER TABLE `INVESTMENT_PROFILE_THEMES`     ADD CONSTRAINT `FK_PROFILE_THEMES_PROFILE`      FOREIGN KEY (`investment_profile_id`) REFERENCES `INVESTMENT_PROFILE`(`id`);
ALTER TABLE `CATEGORY_SPARE_CHANGE_RULES`   ADD CONSTRAINT `FK_CATEGORY_RULES_USER`         FOREIGN KEY (`user_id`) REFERENCES `USERS`(`id`);
ALTER TABLE `LINKED_ACCOUNTS`               ADD CONSTRAINT `FK_LINKED_ACCOUNTS_USER`        FOREIGN KEY (`user_id`) REFERENCES `USERS`(`id`);
ALTER TABLE `PORTFOLIOS`                    ADD CONSTRAINT `FK_PORTFOLIOS_USER`             FOREIGN KEY (`user_id`) REFERENCES `USERS`(`id`);
ALTER TABLE `PAYMENT_EVENTS`                ADD CONSTRAINT `FK_PAYMENT_EVENTS_COIN`         FOREIGN KEY (`target_coin_market`) REFERENCES `COIN_METADATA`(`market`);
ALTER TABLE `DEVICE_TOKENS`                 ADD CONSTRAINT `FK_DEVICE_TOKENS_USER`          FOREIGN KEY (`user_id`) REFERENCES `USERS`(`id`);
-- 참고: PAYMENT_EVENTS.user_id 는 USERS를 가리키지만 JPA 연관관계 없이 Long 스칼라로 보관(느슨한 결합).
--       결제 원장은 유저 엔티티에 의존하지 않고 독립적으로 적재/조회된다.
```

---

## 테이블 관계 요약

```
USERS (1)
 ├── INVESTMENT_PROFILE (1)              - 가상자산 투자 성향(5축)
 │    └── INVESTMENT_PROFILE_THEMES (N)  - 관심 코인 테마 (1:N 다중 선택, Set)
 ├── CATEGORY_SPARE_CHANGE_RULES (N)     - 카테고리별 잔돈 규칙 (user_id+category UNIQUE)
 ├── LINKED_ACCOUNTS (1)                 - 금융 연동 정보 (Upbit API 키, 타겟 카드, 2차 인증)
 ├── PORTFOLIOS (N)                      - 보유 종목 현황 (user_id+market UNIQUE)
 ├── DEVICE_TOKENS (N)                   - FCM 디바이스 토큰 (fcm_token UNIQUE, @ManyToOne FK)
 └── PAYMENT_EVENTS (N)                  - 결제 이벤트 (JPA 연관관계 없이 user_id 스칼라 보관)

PAYMENT_EVENTS (N) ──> COIN_METADATA (1)  - target_coin_market FK (실시간 퀀트 스코어링 기반 매수 타겟 코인)

PAYMENT_CATEGORY_MAPPING  - 전역 키워드→카테고리 사전 (유저 무관, AI 분류 결과 누적 캐시)
AI_RECOMMENDATION_HISTORY - AI 매크로 유니버스 후보군 (12시간 주기 9개 성향 조합별 캐싱)
COIN_METADATA             - 업비트 마켓 메타데이터 (매일 동기화)

인사이트 (독립 테이블, FK 없음)
 ├── MARKET_TOPICS          외부 RSS 주기 수집
 ├── INVESTMENT_TERMS       시딩
 ├── BEGINNER_ARTICLES      시딩
 └── RECOMMENDED_VIDEOS     시딩
```

---

## 주요 상태값 (Enum)

| 테이블/엔티티 | 컬럼/필드 | 값 |
|---|---|---|
| `PAYMENT_EVENTS` | `status` | `NOT_INVESTED`, `PENDING_PURCHASE`, `PENDING_DEPOSIT`, `PENDING_TRADE`, `INVESTED`, `FAILED` |
| `PAYMENT_EVENTS` / `CATEGORY_SPARE_CHANGE_RULES` | `category` | `CAFE`, `MART`, `FOOD`, `SHOPPING`, `TRAFFIC`, `CULTURE`, `ETC` |
| `CATEGORY_SPARE_CHANGE_RULES` | `rule_type` | `ROUND_UP_10000`, `ROUND_UP_20000`, `ROUND_UP_30000`, `ROUND_UP_40000`, `ROUND_UP_50000`, `PERCENT_10`, `PERCENT_15`, `PERCENT_20`, `PERCENT_25`, `PERCENT_30` |
| `LINKED_ACCOUNTS` | `two_factor_provider` | `KAKAO`, `NAVER`, `HANA` — 업비트 원화 입금 2차 인증 수단. 각각 업비트 API에 `kakao` / `naver` / `hana` 문자열로 전달된다 |
| `LINKED_ACCOUNTS` | `target_card_company` | 컬럼 타입은 `VARCHAR`(String)이며 enum 매핑이 아니다. 다만 `TargetCardCompany` enum이 상수로 존재하고 값은 **`KBANK`(케이뱅크) 하나뿐**이다 (현재 케이뱅크 카드 단일 지원) |
| `INVESTMENT_PROFILE` | `risk_tolerance` | `SELL_IMMEDIATELY`(2), `HOLD`(5), `BUY_MORE`(9) — *괄호 안은 성향 점수* |
| `INVESTMENT_PROFILE` | `trend_sensitivity` | `FUNDAMENTAL_ONLY`(2), `PARTIAL_TREND`(5), `FULL_TREND`(9) |
| `INVESTMENT_PROFILE` | `diversification_type` | `CONCENTRATED`, `BALANCED`, `DIVERSIFIED` |
| `INVESTMENT_PROFILE` | `meme_acceptance` | `NONE`(0%), `SMALL`(10%), `ACTIVE`(30%) — *괄호 안은 최대 편입 비중* |
| `INVESTMENT_PROFILE_THEMES` | `theme` | `LAYER_1`, `DEFI`, `AI`, `WEB3_GAMING`, `RWA`, `MEME` |
| `NotificationType` (DB 컬럼 아님, FCM 발송 종류) | — | `TRADE_SUCCESS`, `TRADE_TIMEOUT`, `DEPOSIT_FAILED`, `TRADE_FAILED`, `UPBIT_INVALID_KEY`, `ORDER_EXPIRED` — 각 값은 고정 제목(title)과 딥링크(deepLink)를 가진다. `DEVICE_TOKENS`에 저장되지 않고 발송 시점에만 사용 |

### PaymentStatus 전이 흐름

```
[결제 수신]
   ├── 잔돈 0원 / 최소 투자 금액(5,100원) 미달 ──> NOT_INVESTED (종료)
   └── 코인 추천 완료 ──> PENDING_PURCHASE
                            ├── [유저 거절] ─────────> NOT_INVESTED
                            ├── [24시간 미응답] ─────> NOT_INVESTED (PendingOrderExpirationScheduler)
                            └── [유저 승인]
                                  └── 업비트 원화 입금 요청(2차 인증) ──> PENDING_DEPOSIT
                                        ├── [2차 인증 210초 초과] ──> PENDING_PURCHASE (복구, 재승인 가능)
                                        ├── [입금 거절/취소] ────────> FAILED
                                        └── [입금 완료 폴링] ──> 매수 주문 ──> PENDING_TRADE
                                              ├── [5초 내 체결] ─────> INVESTED
                                              ├── [체결 폴링] ───────> INVESTED
                                              └── [10분 미체결] ─────> 주문 취소 ──> FAILED
```
> **복구 전이 주의:** 2차 인증 타임아웃(210초)은 실패가 아니라 `PENDING_PURCHASE`로 **되돌린다**
> (`PaymentEvent.revertToPendingPurchase()` — `deposit_uuid`와 `deposit_requested_at`을 함께 `NULL`로 초기화).
> 사용자가 다시 승인할 수 있으며, 이 건은 24시간 만료 규칙을 그대로 따른다.
>
> 실패(입금 거절, 매수 주문 실패, 키 만료, 체결량 0, 10분 타임아웃 등)는 어느 단계에서든 `FAILED` + `reason` 기록.
