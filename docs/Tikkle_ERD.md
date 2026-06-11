# Tikkle DB 스키마 (DDL)

> 이 파일을 참고하여 API 설계, 쿼리 작성, 기능 개발 시 테이블 구조를 파악하세요.

---

## 테이블 목록

| 테이블명 | 설명 |
|---------|------|
| `USERS` | 회원 기본 정보, 타겟 카드 정보 포함 |
| `INVESTMENT_PROFILES` | 유저별 9축 투자 성향 설문 결과 |
| `CATEGORY_SPARE_CHANGE_RULES` | 카테고리별 다이나믹 잔돈 규칙 |
| `LINKED_ACCOUNTS` | 금융 연동 정보 (한투 API 키) |
| `INVESTMENT_SETTINGS` | 매매 방식 등 공통 투자 설정 |
| `PAYMENT_EVENTS` | 결제 이벤트 원장 |
| `PAYMENT_CATEGORY_MAPPINGS` | AI 분류 결과 |
| `STOCKS` | 투자 가능 종목 마스터 |
| `INVESTMENT_TARGETS` | 카테고리별/기본 투자 종목 지정 |
| `INVESTMENT_ORDERS` | 매수/매도 주문 원장 |
| `PORTFOLIOS` | 보유 종목 현황 |
| `MARKET_TOPICS` | 투데이 마켓 토픽 (뉴스, Google News RSS 수집) |
| `INVESTMENT_TERMS` | 투자 용어집 (시딩) |
| `BEGINNER_ARTICLES` | 초보자 가이드 글 (시딩, 앱 내부 렌더링) |
| `RECOMMENDED_VIDEOS` | 추천 영상 (시딩, 외부 링크) |


---

## DDL

```sql
CREATE TABLE `USERS` (
    `id`                            BIGINT          NOT NULL    AUTO_INCREMENT,
    `name`                          VARCHAR(50)     NOT NULL,
    `email`                         VARCHAR(100)    NOT NULL,
    `provider`                      VARCHAR(20)     NOT NULL,   -- GOOGLE, KAKAO 등
    `provider_id`                   VARCHAR(255)    NULL,       -- 소셜 로그인 고유 ID
    `status`                        VARCHAR(20)     NOT NULL,   -- ACTIVE, WITHDRAWN
    `target_card_company`           VARCHAR(50)     NULL,
    `target_card_number_last_4`     VARCHAR(4)      NULL,
    `created_at`                    DATETIME        NOT NULL,
    `deleted_at`                    DATETIME        NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UQ_USERS_EMAIL` (`email`)
);

CREATE TABLE `PAYMENT_EVENTS` (
    `id`                    BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`               BIGINT          NOT NULL,
    `card_company`          VARCHAR(50)     NOT NULL,
    `card_number_last_4`    VARCHAR(4)      NOT NULL,
    `merchant`              VARCHAR(100)    NOT NULL,
    `amount`                INT             NOT NULL,
    `spare_change`          INT             NOT NULL,
    `status`                VARCHAR(20)     NOT NULL,   -- PENDING, CLASSIFYING, INVESTED, NOT_INVESTED
    `transaction_id`        VARCHAR(255)    NOT NULL,
    `reason`                VARCHAR(255)    NULL,       -- 미투자 사유 등
    `created_at`            DATETIME        NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UQ_PAYMENT_EVENTS_TRANSACTION_ID` (`transaction_id`)
);

CREATE TABLE `INVESTMENT_PROFILES` (
    `id`                        BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`                   BIGINT          NOT NULL,
    `risk_tolerance`            VARCHAR(20)     NOT NULL,   -- SAFE, MODERATE, AGGRESSIVE
    `investment_term`           VARCHAR(20)     NOT NULL,   -- SHORT_TERM, LONG_TERM
    `investment_style`          VARCHAR(20)     NOT NULL,   -- VALUE, MOMENTUM
    `preferred_theme`           VARCHAR(30)     NOT NULL,   -- TECH, BIO, SEMICONDUCTOR, GREEN, ENTERTAINMENT, NONE
    `stock_cap_preference`      VARCHAR(20)     NOT NULL,   -- BLUE_CHIP, NEW_LISTING
    `market_preference`         VARCHAR(20)     NOT NULL,   -- DOMESTIC, FOREIGN, BOTH
    `esg_focus`                 VARCHAR(20)     NOT NULL,   -- NONE, ESG_DRIVEN
    `sin_industry_filter`       VARCHAR(30)     NOT NULL,   -- NONE, WEAPON, TOBACCO, FOSSIL_FUEL
    `return_preference`         VARCHAR(20)     NOT NULL,   -- DIVIDEND, GROWTH
    `diversification_type`      VARCHAR(20)     NOT NULL,   -- CONCENTRATED, DIVERSIFIED
    PRIMARY KEY (`id`),
    UNIQUE KEY `UQ_INVESTMENT_PROFILES_USER_ID` (`user_id`)
);

CREATE TABLE `CATEGORY_SPARE_CHANGE_RULES` (
    `id`            BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`       BIGINT          NOT NULL,
    `category`      VARCHAR(30)     NOT NULL,   -- CAFE, MART, FOOD, SHOPPING, TRAFFIC, CULTURE, ETC
    `rule_type`     VARCHAR(30)     NOT NULL,   -- ROUND_UP_1000, ROUND_UP_5000, ROUND_UP_10000, PERCENT_10
    `is_active`     BOOLEAN         NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UQ_CATEGORY_RULES_USER_CATEGORY` (`user_id`, `category`)
);

CREATE TABLE `LINKED_ACCOUNTS` (
    `id`                    BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`               BIGINT          NOT NULL,
    `kis_app_key`           VARCHAR(255)    NOT NULL,   -- AES-256 암호화
    `kis_app_secret`        VARCHAR(255)    NOT NULL,   -- AES-256 암호화
    `kis_account_num`       VARCHAR(255)    NOT NULL,   -- AES-256 암호화
    PRIMARY KEY (`id`),
    UNIQUE KEY `UQ_LINKED_ACCOUNTS_USER_ID` (`user_id`)
);

CREATE TABLE `INVESTMENT_SETTINGS` (
    `id`                BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`           BIGINT          NOT NULL,
    `execution_mode`    VARCHAR(20)     NOT NULL,   -- AUTO, MANUAL
    PRIMARY KEY (`id`),
    UNIQUE KEY `UQ_INVESTMENT_SETTINGS_USER_ID` (`user_id`)
);

CREATE TABLE `PAYMENT_CATEGORY_MAPPINGS` (
    `id`                BIGINT          NOT NULL    AUTO_INCREMENT,
    `payment_event_id`  BIGINT          NOT NULL,
    `category`          VARCHAR(30)     NOT NULL,
    `classified_by`     VARCHAR(20)     NOT NULL,   -- TRIE_HIT, AI, DEFAULT
    `confidence`        DECIMAL(5,4)    NULL,
    `classified_at`     DATETIME        NOT NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE `STOCKS` (
    `id`            BIGINT          NOT NULL    AUTO_INCREMENT,
    `ticker`        VARCHAR(20)     NOT NULL,
    `name`          VARCHAR(100)    NOT NULL,
    `industry_code` VARCHAR(20)     NOT NULL,
    `exchange`      VARCHAR(20)     NOT NULL,   -- KRX, NASDAQ, NYSE
    `is_active`     BOOLEAN         NOT NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE `INVESTMENT_TARGETS` (
    `id`            BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`       BIGINT          NOT NULL,
    `category`      VARCHAR(30)     NULL,       -- 카테고리 (NULL이면 기본 투자 종목)
    `stock_id`      BIGINT          NOT NULL,   -- 투자 대상이 될 단일 종목 ID
    `updated_at`    DATETIME        NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UQ_USER_CATEGORY_TARGET` (`user_id`, `category`)
);

CREATE TABLE `INVESTMENT_ORDERS` (
    `id`                BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`           BIGINT          NOT NULL,
    `payment_event_id`  BIGINT          NOT NULL,
    `stock_id`          BIGINT          NOT NULL,
    `amount`            DECIMAL(15,2)   NOT NULL,
    `quantity`          DECIMAL(15,6)   NULL,
    `price`             DECIMAL(15,2)   NULL,
    `order_type`        VARCHAR(10)     NOT NULL    DEFAULT 'BUY',  -- BUY, SELL
    `status`            VARCHAR(20)     NOT NULL    DEFAULT 'PENDING',  -- PENDING, EXECUTED, FAILED
    `kis_order_no`      VARCHAR(20)     NULL,       -- 한투 API 주문번호
    `ord_dvsn`          VARCHAR(5)      NULL,       -- 00:지정가, 01:시장가
    `reject_reason`     VARCHAR(200)    NULL,
    `ordered_at`        DATETIME        NOT NULL,
    `executed_at`       DATETIME        NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE `PORTFOLIOS` (
    `id`                BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`           BIGINT          NOT NULL,
    `stock_id`          BIGINT          NOT NULL,
    `quantity`          DECIMAL(15,6)   NOT NULL,
    `avg_buy_price`     DECIMAL(15,2)   NOT NULL,
    `total_buy_amount`  DECIMAL(15,2)   NOT NULL,
    `current_price`     DECIMAL(15,2)   NOT NULL,
    `evaluated_amount`  DECIMAL(15,2)   NOT NULL,
    `evlu_pfls_amt`     DECIMAL(15,2)   NULL,       -- 한투 API 평가손익금액
    `evlu_pfls_rt`      DECIMAL(10,4)   NULL,       -- 한투 API 평가손익율
    `updated_at`        DATETIME        NOT NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE `MARKET_TOPICS` (
     `id`                BIGINT          NOT NULL,
     `title`             VARCHAR(300)    NOT NULL,
     `press`             VARCHAR(100)    NULL,                       -- 매체명
     `link`              VARCHAR(500)    NOT NULL,                   -- 원문 외부 링크 (UNIQUE)
     `summary`           VARCHAR(500)    NULL,
     `thumbnail_url`     VARCHAR(500)    NULL,                       -- 대부분 null (RSS 미제공)
     `published_at`      DATETIME        NULL,
     `keyword`           VARCHAR(50)     NULL,                       -- 수집 키워드 (코스피/코스닥/증시/주식)
     `fetched_at`        DATETIME        NOT NULL                    -- 수집 시각
);

CREATE TABLE `INVESTMENT_TERMS` (
    `id`                BIGINT          NOT NULL,
    `term`              VARCHAR(100)    NOT NULL,
    `description`       TEXT            NOT NULL,
    `display_order`     INT             NOT NULL
);

CREATE TABLE `BEGINNER_ARTICLES` (
     `id`                BIGINT          NOT NULL,
     `title`             VARCHAR(200)    NOT NULL,
     `body`              TEXT            NOT NULL,                    -- 본문 전체 (앱 내부 렌더링)
     `thumbnail_url`     VARCHAR(500)    NULL,
     `display_order`     INT             NOT NULL,
     `published_at`      DATETIME        NULL
);

CREATE TABLE `RECOMMENDED_VIDEOS` (
      `id`                BIGINT          NOT NULL,
      `title`             VARCHAR(200)    NOT NULL,
      `video_url`         VARCHAR(500)    NOT NULL,                   -- 유튜브 외부 링크
      `thumbnail_url`     VARCHAR(500)    NULL,
      `channel_name`      VARCHAR(100)    NULL,
      `display_order`     INT             NOT NULL
);



```

---

## 테이블 관계 요약

```
USERS (1)
 ├── INVESTMENT_PROFILES (1)          - 유저별 9축 투자 성향
 ├── CATEGORY_SPARE_CHANGE_RULES (N)  - 카테고리별 잔돈 규칙
 ├── LINKED_ACCOUNTS (1)              - 금융 연동 정보 (API 키)
 ├── INVESTMENT_SETTINGS (1)          - 공통 투자 설정
 ├── PAYMENT_EVENTS (N)               - 결제 이벤트
 │    └── PAYMENT_CATEGORY_MAPPINGS (1) - AI 분류 결과
 ├── INVESTMENT_TARGETS (N)           - 카테고리별/기본 투자 종목 지정
 ├── INVESTMENT_ORDERS (N)            - 매수/매도 주문 원장
 └── PORTFOLIOS (N)                   - 보유 종목 현황

STOCKS
 ├── INVESTMENT_TARGETS     (1:N)
 ├── INVESTMENT_ORDERS      (1:N)
 └── PORTFOLIOS             (1:N)

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
| `USERS` | `provider` | `GOOGLE` (*AuthProvider.java 참고*) |
| `PAYMENT_EVENTS` | `status` | `NOT_INVESTED`, `CLASSIFYING`, `PENDING`, `WAITING_APPROVAL`, `ORDERING`, `INVESTED`, `FAILED`, `EXPIRED` |
| `INVESTMENT_ORDERS` | `status` | `PENDING`, `EXECUTED`, `FAILED` |
| `INVESTMENT_SETTINGS` | `executionMode` | `AUTO`, `MANUAL` |
| `CATEGORY_SPARE_CHANGE_RULES` | `category` | `CAFE`, `MART`, `FOOD`, `SHOPPING`, `TRAFFIC`, `CULTURE`, `ETC` |
| `CATEGORY_SPARE_CHANGE_RULES` | `ruleType` | `ROUND_UP_1000`, `ROUND_UP_5000`, `ROUND_UP_10000`, `PERCENT_10` |
| `INVESTMENT_PROFILES` | ... | ... |
```