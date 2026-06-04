# NREDI DB 스키마 (DDL)

> 이 파일을 참고하여 API 설계, 쿼리 작성, 기능 개발 시 테이블 구조를 파악하세요.

---

## 테이블 목록

| 테이블명 | 설명 |
|---------|------|
| `USERS` | 회원 기본 정보 |
| `LINKED_ACCOUNTS` | 연동 계좌 (결제/투자), 한투 API 키 관리 |
| `INVESTMENT_PROFILES` | 투자 성향 설문 결과 |
| `INVESTMENT_SETTINGS` | 유저별 앱 동작 설정 |
| `PAYMENT_EVENTS` | 결제 이벤트 원장 |
| `PAYMENT_CATEGORIES` | 결제 카테고리 마스터 |
| `PAYMENT_CATEGORY_MAPPINGS` | AI 분류 결과 |
| `STOCKS` | 투자 가능 종목 마스터 |
| `ALLOCATION_RULES` | 종목별 투자 배분 비율 |
| `INVESTMENT_ORDERS` | 매수/매도 주문 원장 |
| `PORTFOLIOS` | 보유 종목 현황 |
| `PORTFOLIO_SNAPSHOTS` | 일별 포트폴리오 스냅샷 |
| `STOCK_CONTENTS` | 토픽/인사이트/초보자 정보 콘텐츠 |
| `NOTIFICATIONS` | 푸시 알림 이력 |

---

## DDL

```sql
CREATE TABLE `USERS` (
    `id`            BIGINT          NOT NULL,
    `name`          VARCHAR(50)     NOT NULL,
    `email`         VARCHAR(100)    NOT NULL,
    `provider`      VARCHAR(20)     NOT NULL,                       -- GOOGLE
    `provider_id`   VARCHAR(100)    NULL,                           -- 소셜 로그인 고유 ID (구글 sub)
    `status`        VARCHAR(20)     NOT NULL    DEFAULT 'ACTIVE',   -- ACTIVE, WITHDRAWN
    `created_at`    DATETIME        NOT NULL,
    `deleted_at`    DATETIME        NULL
);

CREATE TABLE `LINKED_ACCOUNTS` (
    `id`                            BIGINT          NOT NULL,
    `user_id`                       BIGINT          NOT NULL,
    `account_type`                  VARCHAR(20)     NOT NULL,       -- PAYMENT, INVESTMENT
    `bank_code`                     VARCHAR(10)     NOT NULL,
    `account_number`                VARCHAR(30)     NOT NULL,
    `account_holder`                VARCHAR(50)     NOT NULL,
    `cano`                          VARCHAR(10)     NULL,           -- 한투 API 종합계좌번호 (8자리)
    `acnt_prdt_cd`                  VARCHAR(5)      NULL,           -- 한투 API 계좌상품코드 (ex: '01')
    `kis_app_key`                   VARCHAR(100)    NULL,           -- 한투 API AppKey (암호화 저장)
    `kis_app_secret`                VARCHAR(200)    NULL,           -- 한투 API AppSecret (암호화 저장)
    `kis_access_token`              VARCHAR(500)    NULL,           -- 발급받은 액세스 토큰
    `kis_access_token_expired_at`   DATETIME        NULL,           -- 토큰 만료 일시
    `is_active`                     BOOLEAN         NOT NULL,
    `linked_at`                     DATETIME        NOT NULL
);

CREATE TABLE `INVESTMENT_PROFILES` (
    `id`            BIGINT          NOT NULL,
    `user_id`       BIGINT          NOT NULL,
    `risk_score`    INT             NOT NULL,
    `risk_grade`    VARCHAR(20)     NOT NULL,                       -- CONSERVATIVE, MODERATE, AGGRESSIVE
    `surveyed_at`   DATETIME        NOT NULL
);

CREATE TABLE `INVESTMENT_SETTINGS` (
    `id`                                BIGINT          NOT NULL,
    `user_id`                           BIGINT          NOT NULL,
    `execution_mode`                    VARCHAR(10)     NOT NULL    DEFAULT 'AUTO',    -- AUTO, MANUAL
    `spare_change_rule`                 VARCHAR(10)     NOT NULL    DEFAULT 'FIXED',   -- FIXED, RATIO
    `spare_change_threshold`            INT             NOT NULL,
    `spare_change_ratio`                DECIMAL(5,2)    NULL,
    `auto_reflect_payment_category`     BOOLEAN         NOT NULL,
    `notify_payment`                    BOOLEAN         NOT NULL,
    `notify_report`                     BOOLEAN         NOT NULL,
    `updated_at`                        DATETIME        NOT NULL
);

CREATE TABLE `PAYMENT_EVENTS` (
    `id`                    BIGINT          NOT NULL,
    `user_id`               BIGINT          NOT NULL,
    `merchant_name`         VARCHAR(100)    NOT NULL,
    `amount`                DECIMAL(15,2)   NOT NULL,
    `spare_change_amount`   DECIMAL(15,2)   NOT NULL,
    `status`                VARCHAR(20)     NOT NULL    DEFAULT 'PENDING',  -- PENDING, CLASSIFIED, INVESTED, FAILED
    `paid_at`               DATETIME        NOT NULL,
    `created_at`            DATETIME        NOT NULL
);

CREATE TABLE `PAYMENT_CATEGORIES` (
    `id`            BIGINT          NOT NULL,
    `name`          VARCHAR(50)     NOT NULL,
    `industry_code` VARCHAR(20)     NOT NULL
);

CREATE TABLE `PAYMENT_CATEGORY_MAPPINGS` (
    `id`                BIGINT          NOT NULL,
    `payment_event_id`  BIGINT          NOT NULL,
    `category_id`       BIGINT          NOT NULL,
    `classified_by`     VARCHAR(20)     NOT NULL,                   -- TRIE_HIT, AI, DEFAULT
    `confidence`        DECIMAL(5,4)    NULL,
    `classified_at`     DATETIME        NOT NULL
);

CREATE TABLE `STOCKS` (
    `id`            BIGINT          NOT NULL,
    `ticker`        VARCHAR(20)     NOT NULL,
    `name`          VARCHAR(100)    NOT NULL,
    `industry_code` VARCHAR(20)     NOT NULL,
    `exchange`      VARCHAR(20)     NOT NULL,                       -- KRX, NASDAQ, NYSE
    `is_active`     BOOLEAN         NOT NULL
);

CREATE TABLE `ALLOCATION_RULES` (
    `id`            BIGINT          NOT NULL,
    `user_id`       BIGINT          NOT NULL,
    `stock_id`      BIGINT          NOT NULL,
    `category_id`   BIGINT          NULL,                           -- NULL이면 기본 성향 배분
    `weight`        DECIMAL(5,4)    NOT NULL,                       -- 합계 = 1.0000
    `source`        VARCHAR(20)     NOT NULL,                       -- PROFILE, PAYMENT_CATEGORY
    `updated_at`    DATETIME        NOT NULL
);

CREATE TABLE `INVESTMENT_ORDERS` (
    `id`                BIGINT          NOT NULL,
    `user_id`           BIGINT          NOT NULL,
    `payment_event_id`  BIGINT          NOT NULL,
    `stock_id`          BIGINT          NOT NULL,
    `amount`            DECIMAL(15,2)   NOT NULL,
    `quantity`          DECIMAL(15,6)   NULL,
    `price`             DECIMAL(15,2)   NULL,
    `order_type`        VARCHAR(10)     NOT NULL    DEFAULT 'BUY',  -- BUY, SELL
    `status`            VARCHAR(20)     NOT NULL    DEFAULT 'PENDING',  -- PENDING, EXECUTED, FAILED
    `kis_order_no`      VARCHAR(20)     NULL,                       -- 한투 API 주문번호
    `ord_dvsn`          VARCHAR(5)      NULL,                       -- 00:지정가, 01:시장가
    `reject_reason`     VARCHAR(200)    NULL,
    `ordered_at`        DATETIME        NOT NULL,
    `executed_at`       DATETIME        NULL
);

CREATE TABLE `PORTFOLIOS` (
    `id`                BIGINT          NOT NULL,
    `user_id`           BIGINT          NOT NULL,
    `stock_id`          BIGINT          NOT NULL,
    `quantity`          DECIMAL(15,6)   NOT NULL,
    `avg_buy_price`     DECIMAL(15,2)   NOT NULL,
    `total_buy_amount`  DECIMAL(15,2)   NOT NULL,
    `current_price`     DECIMAL(15,2)   NOT NULL,
    `evaluated_amount`  DECIMAL(15,2)   NOT NULL,
    `evlu_pfls_amt`     DECIMAL(15,2)   NULL,                       -- 한투 API 평가손익금액
    `evlu_pfls_rt`      DECIMAL(10,4)   NULL,                       -- 한투 API 평가손익율
    `updated_at`        DATETIME        NOT NULL
);

CREATE TABLE `PORTFOLIO_SNAPSHOTS` (
    `id`                        BIGINT          NOT NULL,
    `user_id`                   BIGINT          NOT NULL,
    `total_buy_amount`          DECIMAL(15,2)   NOT NULL,
    `total_evaluated_amount`    DECIMAL(15,2)   NOT NULL,
    `profit_loss`               DECIMAL(15,2)   NOT NULL,
    `snapshot_date`             DATE            NOT NULL
);

CREATE TABLE `STOCK_CONTENTS` (
    `id`                BIGINT          NOT NULL,
    `content_type`      VARCHAR(20)     NOT NULL,                   -- TOPIC, INSIGHT, BEGINNER
    `title`             VARCHAR(200)    NOT NULL,
    `body`              TEXT            NOT NULL,
    `target_audience`   VARCHAR(20)     NOT NULL    DEFAULT 'ALL',  -- ALL, BEGINNER, ADVANCED
    `stock_id`          BIGINT          NULL,
    `published_at`      DATETIME        NOT NULL
);

CREATE TABLE `NOTIFICATIONS` (
    `id`            BIGINT          NOT NULL,
    `user_id`       BIGINT          NOT NULL,
    `type`          VARCHAR(30)     NOT NULL,                       -- PAYMENT_DETECTED, INVEST_SUCCESS, INVEST_FAILED, REPORT
    `title`         VARCHAR(100)    NOT NULL,
    `body`          VARCHAR(500)    NOT NULL,
    `deep_link`     VARCHAR(300)    NULL,
    `is_read`       BOOLEAN         NOT NULL,
    `is_deleted`    BOOLEAN         NOT NULL,                       -- 소프트 딜리트
    `sent_at`       DATETIME        NOT NULL
);

-- PRIMARY KEY
ALTER TABLE `USERS`                     ADD CONSTRAINT `PK_USERS`                       PRIMARY KEY (`id`);
ALTER TABLE `LINKED_ACCOUNTS`           ADD CONSTRAINT `PK_LINKED_ACCOUNTS`             PRIMARY KEY (`id`);
ALTER TABLE `INVESTMENT_PROFILES`       ADD CONSTRAINT `PK_INVESTMENT_PROFILES`         PRIMARY KEY (`id`);
ALTER TABLE `INVESTMENT_SETTINGS`       ADD CONSTRAINT `PK_INVESTMENT_SETTINGS`         PRIMARY KEY (`id`);
ALTER TABLE `PAYMENT_EVENTS`            ADD CONSTRAINT `PK_PAYMENT_EVENTS`              PRIMARY KEY (`id`);
ALTER TABLE `PAYMENT_CATEGORIES`        ADD CONSTRAINT `PK_PAYMENT_CATEGORIES`          PRIMARY KEY (`id`);
ALTER TABLE `PAYMENT_CATEGORY_MAPPINGS` ADD CONSTRAINT `PK_PAYMENT_CATEGORY_MAPPINGS`   PRIMARY KEY (`id`);
ALTER TABLE `STOCKS`                    ADD CONSTRAINT `PK_STOCKS`                      PRIMARY KEY (`id`);
ALTER TABLE `ALLOCATION_RULES`          ADD CONSTRAINT `PK_ALLOCATION_RULES`            PRIMARY KEY (`id`);
ALTER TABLE `INVESTMENT_ORDERS`         ADD CONSTRAINT `PK_INVESTMENT_ORDERS`           PRIMARY KEY (`id`);
ALTER TABLE `PORTFOLIOS`                ADD CONSTRAINT `PK_PORTFOLIOS`                  PRIMARY KEY (`id`);
ALTER TABLE `PORTFOLIO_SNAPSHOTS`       ADD CONSTRAINT `PK_PORTFOLIO_SNAPSHOTS`         PRIMARY KEY (`id`);
ALTER TABLE `STOCK_CONTENTS`            ADD CONSTRAINT `PK_STOCK_CONTENTS`              PRIMARY KEY (`id`);
ALTER TABLE `NOTIFICATIONS`             ADD CONSTRAINT `PK_NOTIFICATIONS`               PRIMARY KEY (`id`);

-- UNIQUE
ALTER TABLE `USERS`                     ADD CONSTRAINT `UQ_USERS_EMAIL`                 UNIQUE (`email`);
```

---

## 테이블 관계 요약

```
USERS
 ├── LINKED_ACCOUNTS        (1:N) 오픈뱅킹 연동 계좌, 한투 API 키 포함
 ├── INVESTMENT_PROFILES    (1:1) 투자 성향 설문 결과
 ├── INVESTMENT_SETTINGS    (1:1) 앱 동작 설정
 ├── PAYMENT_EVENTS         (1:N) 결제 이벤트
 │    └── PAYMENT_CATEGORY_MAPPINGS  (1:N) AI 분류 결과
 │         └── PAYMENT_CATEGORIES    (N:1) 카테고리 마스터
 ├── ALLOCATION_RULES       (1:N) 종목 배분 규칙
 ├── INVESTMENT_ORDERS      (1:N) 매수/매도 주문 원장
 ├── PORTFOLIOS             (1:N) 보유 종목 현황
 ├── PORTFOLIO_SNAPSHOTS    (1:N) 일별 포트폴리오 스냅샷
 └── NOTIFICATIONS          (1:N) 푸시 알림 이력

STOCKS
 ├── ALLOCATION_RULES       (1:N)
 ├── INVESTMENT_ORDERS      (1:N)
 ├── PORTFOLIOS             (1:N)
 └── STOCK_CONTENTS         (1:N) 종목 관련 콘텐츠
```

---

## 주요 상태값 (Enum)

| 테이블 | 컬럼 | 값 |
|--------|------|----|
| `USERS` | `status` | `ACTIVE`, `WITHDRAWN` |
| `USERS` | `provider` | `GOOGLE` |
| `PAYMENT_EVENTS` | `status` | `PENDING`, `CLASSIFIED`, `INVESTED`, `FAILED` |
| `INVESTMENT_ORDERS` | `status` | `PENDING`, `EXECUTED`, `FAILED` |
| `INVESTMENT_ORDERS` | `order_type` | `BUY`, `SELL` |
| `INVESTMENT_ORDERS` | `ord_dvsn` | `00`(지정가), `01`(시장가) |
| `INVESTMENT_PROFILES` | `risk_grade` | `CONSERVATIVE`, `MODERATE`, `AGGRESSIVE` |
| `INVESTMENT_SETTINGS` | `execution_mode` | `AUTO`, `MANUAL` |
| `INVESTMENT_SETTINGS` | `spare_change_rule` | `FIXED`, `RATIO` |
| `LINKED_ACCOUNTS` | `account_type` | `PAYMENT`, `INVESTMENT` |
| `PAYMENT_CATEGORY_MAPPINGS` | `classified_by` | `TRIE_HIT`, `AI`, `DEFAULT` |
| `ALLOCATION_RULES` | `source` | `PROFILE`, `PAYMENT_CATEGORY` |
| `STOCK_CONTENTS` | `content_type` | `TOPIC`, `INSIGHT`, `BEGINNER` |
| `STOCK_CONTENTS` | `target_audience` | `ALL`, `BEGINNER`, `ADVANCED` |
| `NOTIFICATIONS` | `type` | `PAYMENT_DETECTED`, `INVEST_SUCCESS`, `INVEST_FAILED`, `REPORT` |
