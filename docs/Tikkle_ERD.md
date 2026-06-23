# Tikkle DB 스키마 (DDL)

> 이 파일을 참고하여 API 설계, 쿼리 작성, 기능 개발 시 테이블 구조를 파악하세요.
> **본 문서는 `src/main/java/com/tikkle` 엔티티 코드 기준으로 동기화되어 있습니다.**

---

## 테이블 목록

| 테이블명 | 설명 |
|---------|------|
| `USERS` | 회원 기본 정보 |
| `investment_profile` | 유저별 가상자산 투자 성향(5축) |
| `investment_profile_themes` | 투자 프로필별 관심 코인 테마 (다중 선택, Set) |
| `category_spare_change_rules` | 카테고리별 다이나믹 잔돈 규칙 |
| `linked_accounts` | 금융 연동 정보 (Upbit API 키, 타겟 카드 정보) |
| `investment_settings` | 매매 방식(자동/수동) 등 공통 투자 설정 |
| `PAYMENT_EVENTS` | 결제 이벤트 원장 |
| `payment_category_mapping` | 가맹점 키워드 → 카테고리 분류 사전 (전역 캐시, AI 학습 결과 누적) |
| `INVESTMENT_TARGETS` | 일자별 AI 추천 타겟 종목 지정 |
| `PORTFOLIOS` | 보유 종목 현황 |
| `COIN_METADATA` | 업비트 마켓(코인) 메타데이터 (마켓코드/한글명/영문명, 매일 동기화) |
| `MARKET_TOPICS` | 투데이 마켓 토픽 (Google News RSS 수집) |
| `INVESTMENT_TERMS` | 투자 용어집 (시딩) |
| `BEGINNER_ARTICLES` | 초보자 가이드 글 (시딩, 앱 내부 렌더링) |
| `RECOMMENDED_VIDEOS` | 추천 영상 (시딩, 외부 링크) |

> **Redis 저장소 (RDB 외):** 리프레시 토큰(`refresh_token` RedisHash), 결제 멱등성 키(`payment:tx:{transactionId}`, SETNX 24h), 유저 설정 캐시(`user:settings:{userId}` Hash), 스케줄러 분산 락(`scheduler:lock:*`).

---

## DDL

```sql
CREATE TABLE `USERS` (
    `id`            BIGINT          NOT NULL    AUTO_INCREMENT,
    `name`          VARCHAR(50)     NOT NULL,
    `email`         VARCHAR(100)    NOT NULL,
    `provider`      VARCHAR(20)     NOT NULL,   -- AuthProvider: GOOGLE
    `provider_id`   VARCHAR(255)    NULL,       -- 소셜 로그인 고유 ID
    `status`        VARCHAR(20)     NOT NULL,   -- UserStatus: ACTIVE, WITHDRAWN
    `created_at`    DATETIME        NOT NULL,
    `deleted_at`    DATETIME        NULL
);

CREATE TABLE `PAYMENT_EVENTS` (
    `id`                    BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`               BIGINT          NOT NULL,
    `card_company`          VARCHAR(50)     NOT NULL,
    `card_number_last_4`    VARCHAR(4)      NOT NULL,
    `merchant`              VARCHAR(100)    NOT NULL,   -- 분류된 keyword 또는 원본 가맹점명
    `amount`                INT             NOT NULL,
    `spare_change`          INT             NOT NULL,
    `category`              VARCHAR(20)     NULL,       -- PaymentCategory
    `status`                VARCHAR(20)     NOT NULL,   -- PaymentStatus
    `transaction_id`        VARCHAR(255)    NOT NULL,   -- 결정적 해시 기반 고유 ID
    `reason`                VARCHAR(255)    NULL,        -- 미투자/실패 사유 등
    `target_coin_market`    VARCHAR(20)     NULL,       -- FK -> COIN_METADATA(market)
    `created_at`            DATETIME        NOT NULL
);

CREATE TABLE `investment_profile` (
    `id`                    BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`               BIGINT          NOT NULL,
    `risk_tolerance`        VARCHAR(30)     NOT NULL,   -- RiskTolerance (하락장 방어 심리)
    `trend_sensitivity`     VARCHAR(30)     NOT NULL,   -- TrendSensitivity (트렌드 민감도)
    `diversification_type`  VARCHAR(30)     NOT NULL,   -- DiversificationType (포트폴리오 분산도)
    `meme_acceptance`       VARCHAR(30)     NOT NULL    -- MemeAcceptance (밈 코인 수용도)
);

CREATE TABLE `investment_profile_themes` (
    `investment_profile_id` BIGINT          NOT NULL,
    `theme`                 VARCHAR(30)     NOT NULL    -- CryptoTheme (관심 코인 테마, 다중)
);

CREATE TABLE `category_spare_change_rules` (
    `id`            BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`       BIGINT          NOT NULL,
    `category`      VARCHAR(30)     NOT NULL,   -- PaymentCategory
    `rule_type`     VARCHAR(30)     NOT NULL    -- RuleType
);

CREATE TABLE `linked_accounts` (
    `id`                    BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`               BIGINT          NOT NULL,
    `upbit_access_key`      VARCHAR(512)    NOT NULL,   -- AES-256 암호화 저장
    `upbit_secret_key`      VARCHAR(512)    NOT NULL,   -- AES-256 암호화 저장
    `target_card_company`   VARCHAR(50)     NOT NULL,
    `target_card_last_4`    VARCHAR(4)      NOT NULL
);

CREATE TABLE `investment_settings` (
    `id`                BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`           BIGINT          NOT NULL,
    `execution_mode`    VARCHAR(20)     NOT NULL    -- ExecutionMode: AUTO, MANUAL
);

CREATE TABLE `payment_category_mapping` (
    `id`            BIGINT          NOT NULL    AUTO_INCREMENT,
    `keyword`       VARCHAR(255)    NOT NULL,   -- 가맹점 핵심 상호명 (UNIQUE)
    `category`      VARCHAR(30)     NOT NULL    -- PaymentCategory
);

CREATE TABLE `INVESTMENT_TARGETS` (
    `id`            BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`       BIGINT          NOT NULL,
    `market`        VARCHAR(20)     NOT NULL,
    `coin_name`     VARCHAR(100)    NOT NULL,
    `reason`        TEXT            NULL,
    `target_date`   DATE            NOT NULL,
    `created_at`    DATETIME        NOT NULL
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

```

---
## CONSTRAINTS

```sql
-- PRIMARY KEY
ALTER TABLE `USERS`                         ADD CONSTRAINT `PK_USERS`                       PRIMARY KEY (`id`);
ALTER TABLE `PAYMENT_EVENTS`                ADD CONSTRAINT `PK_PAYMENT_EVENTS`              PRIMARY KEY (`id`);
ALTER TABLE `investment_profile`            ADD CONSTRAINT `PK_INVESTMENT_PROFILE`          PRIMARY KEY (`id`);
ALTER TABLE `category_spare_change_rules`   ADD CONSTRAINT `PK_CATEGORY_SPARE_CHANGE_RULES` PRIMARY KEY (`id`);
ALTER TABLE `linked_accounts`               ADD CONSTRAINT `PK_LINKED_ACCOUNTS`             PRIMARY KEY (`id`);
ALTER TABLE `investment_settings`           ADD CONSTRAINT `PK_INVESTMENT_SETTINGS`         PRIMARY KEY (`id`);
ALTER TABLE `payment_category_mapping`      ADD CONSTRAINT `PK_PAYMENT_CATEGORY_MAPPING`    PRIMARY KEY (`id`);
ALTER TABLE `INVESTMENT_TARGETS`            ADD CONSTRAINT `PK_INVESTMENT_TARGETS`          PRIMARY KEY (`id`);
ALTER TABLE `PORTFOLIOS`                    ADD CONSTRAINT `PK_PORTFOLIOS`                  PRIMARY KEY (`id`);
ALTER TABLE `COIN_METADATA`                 ADD CONSTRAINT `PK_COIN_METADATA`               PRIMARY KEY (`market`);
ALTER TABLE `MARKET_TOPICS`                 ADD CONSTRAINT `PK_MARKET_TOPICS`               PRIMARY KEY (`id`);
ALTER TABLE `INVESTMENT_TERMS`              ADD CONSTRAINT `PK_INVESTMENT_TERMS`            PRIMARY KEY (`id`);
ALTER TABLE `BEGINNER_ARTICLES`             ADD CONSTRAINT `PK_BEGINNER_ARTICLES`           PRIMARY KEY (`id`);
ALTER TABLE `RECOMMENDED_VIDEOS`            ADD CONSTRAINT `PK_RECOMMENDED_VIDEOS`          PRIMARY KEY (`id`);

-- UNIQUE
ALTER TABLE `USERS`                         ADD CONSTRAINT `UQ_USERS_EMAIL`                 UNIQUE (`email`);
ALTER TABLE `PAYMENT_EVENTS`                ADD CONSTRAINT `UQ_PAYMENT_EVENTS_TX_ID`        UNIQUE (`transaction_id`);
ALTER TABLE `investment_profile`            ADD CONSTRAINT `UQ_INVESTMENT_PROFILE_USER`     UNIQUE (`user_id`);
ALTER TABLE `category_spare_change_rules`   ADD CONSTRAINT `UQ_CATEGORY_RULES_USER_CAT`     UNIQUE (`user_id`, `category`);
ALTER TABLE `linked_accounts`               ADD CONSTRAINT `UQ_LINKED_ACCOUNTS_USER`        UNIQUE (`user_id`);
ALTER TABLE `investment_settings`           ADD CONSTRAINT `UQ_INVESTMENT_SETTINGS_USER`    UNIQUE (`user_id`);
ALTER TABLE `payment_category_mapping`      ADD CONSTRAINT `UQ_PAYMENT_CAT_MAPPING_KEYWORD` UNIQUE (`keyword`);
ALTER TABLE `INVESTMENT_TARGETS`            ADD CONSTRAINT `UQ_USER_TARGET_DATE`            UNIQUE (`user_id`, `target_date`);
ALTER TABLE `PORTFOLIOS`                    ADD CONSTRAINT `UQ_USER_MARKET`                 UNIQUE (`user_id`, `market`);
ALTER TABLE `MARKET_TOPICS`                 ADD CONSTRAINT `UQ_MARKET_TOPICS_LINK`          UNIQUE (`link`);

-- FOREIGN KEY (JPA 연관관계)
ALTER TABLE `PAYMENT_EVENTS`                ADD CONSTRAINT `FK_PAYMENT_EVENTS_COIN`         FOREIGN KEY (`target_coin_market`) REFERENCES `COIN_METADATA`(`market`);
-- 참고: PAYMENT_EVENTS.user_id 는 FK 매핑 없이 Long 스칼라로 보관(느슨한 결합).
```

---

## 테이블 관계 요약

```
USERS (1)
 ├── investment_profile (1)              - 가상자산 투자 성향(5축)
 │    └── investment_profile_themes (N)  - 관심 코인 테마 (1:N 다중 선택, Set)
 ├── category_spare_change_rules (N)     - 카테고리별 잔돈 규칙 (user_id+category UNIQUE)
 ├── linked_accounts (1)                 - 금융 연동 정보 (Upbit API 키, 타겟 카드)
 ├── investment_settings (1)             - 공통 투자 설정 (자동/수동)
 ├── PAYMENT_EVENTS (N)                  - 결제 이벤트 (user_id 스칼라 보관)
 ├── INVESTMENT_TARGETS (N)              - 일자별 AI 추천 타겟 (user_id+target_date UNIQUE)
 └── PORTFOLIOS (N)                      - 보유 종목 현황 (user_id+market UNIQUE)

PAYMENT_EVENTS (N) ──> COIN_METADATA (1)  - target_coin_market FK (당일 배정 타겟 코인)

payment_category_mapping  - 전역 키워드→카테고리 사전 (유저 무관, AI 분류 결과 누적 캐시)
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
| `USERS` | `status` | `ACTIVE`, `WITHDRAWN` |
| `USERS` | `provider` | `GOOGLE` |
| `PAYMENT_EVENTS` | `status` | `NOT_INVESTED`, `CLASSIFYING`, `WAITING_APPROVAL`, `ORDERING`, `INVESTED`, `FAILED` |
| `PAYMENT_EVENTS` / `category_spare_change_rules` | `category` | `CAFE`, `MART`, `FOOD`, `SHOPPING`, `TRAFFIC`, `CULTURE`, `ETC` |
| `category_spare_change_rules` | `rule_type` | `ROUND_UP_10000`, `ROUND_UP_20000`, `ROUND_UP_30000`, `ROUND_UP_40000`, `ROUND_UP_50000`, `PERCENT_10`, `PERCENT_15`, `PERCENT_20`, `PERCENT_25`, `PERCENT_30` |
| `investment_settings` | `execution_mode` | `AUTO`, `MANUAL` |
| `investment_profile` | `risk_tolerance` | `SELL_IMMEDIATELY`(2), `HOLD`(5), `BUY_MORE`(9) — *괄호 안은 성향 점수* |
| `investment_profile` | `trend_sensitivity` | `FUNDAMENTAL_ONLY`(2), `PARTIAL_TREND`(5), `FULL_TREND`(9) |
| `investment_profile` | `diversification_type` | `CONCENTRATED`, `BALANCED`, `DIVERSIFIED` |
| `investment_profile` | `meme_acceptance` | `NONE`(0%), `SMALL`(10%), `ACTIVE`(30%) — *괄호 안은 최대 편입 비중* |
| `investment_profile_themes` | `theme` | `LAYER_1`, `DEFI`, `AI`, `WEB3_GAMING`, `RWA`, `MEME` |