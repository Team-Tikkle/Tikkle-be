-- 기준 스키마. 엔티티가 만드는 스키마(Hibernate DDL)와 같다: 컬럼 순서·타입·제약 이름까지 그대로 옮겼다.
-- 이 파일은 적용된 뒤에는 고치지 않는다. 스키마를 바꿀 때는 새 파일(V2__설명.sql …)을 더한다.
-- 테이블 이름은 소문자다(엔티티의 @Table 이름을 Spring Boot 기본 이름 전략이 소문자로 바꾼다).

CREATE TABLE users (
    created_at   DATETIME(6)  NOT NULL,
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    phone_number VARCHAR(20)  NOT NULL,
    name         VARCHAR(50)  NOT NULL,
    password     VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE coin_metadata (
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at   DATETIME(6)  NOT NULL,
    market       VARCHAR(20)  NOT NULL,
    english_name VARCHAR(100) NOT NULL,
    korean_name  VARCHAR(100) NOT NULL,
    PRIMARY KEY (market)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ai_recommendation_history (
    created_at       DATETIME(6)  NOT NULL,
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    btc_dominance    VARCHAR(255) NOT NULL,
    candidates_json  TEXT         NOT NULL,
    fng_index        VARCHAR(255) NOT NULL,
    hot_narratives   VARCHAR(255),
    macro_events     TEXT,
    profile_hash_key VARCHAR(255) NOT NULL,
    weekly_trend     VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE beginner_articles (
    display_order INTEGER      NOT NULL,
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    published_at  DATETIME(6),
    title         VARCHAR(200) NOT NULL,
    thumbnail_url VARCHAR(500),
    body          TEXT         NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE category_spare_change_rules (
    id        BIGINT NOT NULL AUTO_INCREMENT,
    user_id   BIGINT NOT NULL,
    category  ENUM ('CAFE','CULTURE','ETC','FOOD','MART','SHOPPING','TRAFFIC') NOT NULL,
    rule_type ENUM ('PERCENT_10','PERCENT_15','PERCENT_20','PERCENT_25','PERCENT_30','ROUND_UP_10000','ROUND_UP_20000','ROUND_UP_30000','ROUND_UP_40000','ROUND_UP_50000') NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE device_tokens (
    created_at DATETIME(6)  NOT NULL,
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    updated_at DATETIME(6)  NOT NULL,
    user_id    BIGINT       NOT NULL,
    fcm_token  VARCHAR(512) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE investment_profile (
    id                   BIGINT NOT NULL AUTO_INCREMENT,
    user_id              BIGINT NOT NULL,
    diversification_type ENUM ('BALANCED','CONCENTRATED','DIVERSIFIED'),
    meme_acceptance      ENUM ('ACTIVE','NONE','SMALL'),
    risk_tolerance       ENUM ('BUY_MORE','HOLD','SELL_IMMEDIATELY'),
    trend_sensitivity    ENUM ('FULL_TREND','FUNDAMENTAL_ONLY','PARTIAL_TREND'),
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE investment_profile_themes (
    investment_profile_id BIGINT NOT NULL,
    theme                 ENUM ('AI','DEFI','LAYER_1','MEME','RWA','WEB3_GAMING')
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE investment_terms (
    display_order INTEGER      NOT NULL,
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    term          VARCHAR(100) NOT NULL,
    description   TEXT         NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE linked_accounts (
    is_investment_enabled BIT          NOT NULL,
    target_card_last4     VARCHAR(4),
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    user_id               BIGINT       NOT NULL,
    target_card_company   VARCHAR(50),
    upbit_access_key      VARCHAR(512),
    upbit_secret_key      VARCHAR(512),
    two_factor_provider   ENUM ('HANA','KAKAO','NAVER'),
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE market_topics (
    fetched_at    DATETIME(6)  NOT NULL,
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    published_at  DATETIME(6),
    keyword       VARCHAR(50),
    press         VARCHAR(100),
    title         VARCHAR(300) NOT NULL,
    link          VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500),
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE notices (
    is_pinned    BIT          NOT NULL DEFAULT FALSE,
    is_visible   BIT          NOT NULL DEFAULT TRUE,
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    published_at DATETIME(6)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    content      TEXT         NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE payment_category_mapping (
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    keyword  VARCHAR(255) NOT NULL,
    category ENUM ('CAFE','CULTURE','ETC','FOOD','MART','SHOPPING','TRAFFIC') NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE payment_events (
    amount               INTEGER        NOT NULL,
    card_number_last_4   VARCHAR(4)     NOT NULL,
    invested_price       DECIMAL(30, 8),
    invested_volume      DECIMAL(30, 8),
    spare_change         INTEGER        NOT NULL,
    created_at           DATETIME(6)    NOT NULL,
    deposit_requested_at DATETIME(6),
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    trade_requested_at   DATETIME(6),
    user_id              BIGINT         NOT NULL,
    target_coin_market   VARCHAR(20),
    card_company         VARCHAR(50)    NOT NULL,
    merchant             VARCHAR(100)   NOT NULL,
    raw_merchant         VARCHAR(100)   NOT NULL,
    deposit_uuid         VARCHAR(255),
    reason               VARCHAR(255),
    trade_uuid           VARCHAR(255),
    transaction_id       VARCHAR(255)   NOT NULL,
    category             ENUM ('CAFE','CULTURE','ETC','FOOD','MART','SHOPPING','TRAFFIC'),
    status               ENUM ('FAILED','INVESTED','NOT_INVESTED','PENDING_DEPOSIT','PENDING_PURCHASE','PENDING_TRADE') NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE portfolios (
    average_price DECIMAL(20, 4) NOT NULL,
    quantity      DECIMAL(20, 8) NOT NULL,
    created_at    DATETIME(6)    NOT NULL,
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    user_id       BIGINT         NOT NULL,
    market        VARCHAR(20)    NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE recommended_videos (
    display_order INTEGER      NOT NULL,
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    channel_name  VARCHAR(100),
    title         VARCHAR(200) NOT NULL,
    thumbnail_url VARCHAR(500),
    video_url     VARCHAR(500) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- UNIQUE 제약 (이름은 Hibernate가 만든 그대로)
ALTER TABLE category_spare_change_rules ADD CONSTRAINT UK4diip81ikyakd5lrigg3l8al0 UNIQUE (user_id, category);
ALTER TABLE device_tokens ADD CONSTRAINT UK3shgoehod3h5jkmwsgkxora0u UNIQUE (fcm_token);
ALTER TABLE investment_profile ADD CONSTRAINT UKeo67fi50yxxdwlnlc725noepo UNIQUE (user_id);
ALTER TABLE linked_accounts ADD CONSTRAINT UKlc8mut9rnu6qm8yil5npmsth5 UNIQUE (user_id);
ALTER TABLE market_topics ADD CONSTRAINT UK3oxp8vgavvilyal9265kun65h UNIQUE (link);
ALTER TABLE payment_category_mapping ADD CONSTRAINT UKgmuuba6bptlef1x6vr7el41r5 UNIQUE (keyword);
ALTER TABLE payment_events ADD CONSTRAINT UKsikh50mhmgwy6gn19yvpooiwu UNIQUE (deposit_uuid);
ALTER TABLE payment_events ADD CONSTRAINT UKjejv06tn576vvh97j8ax0k3bb UNIQUE (trade_uuid);
ALTER TABLE payment_events ADD CONSTRAINT UKq0galayxthqp8n6ebm0btsc3w UNIQUE (transaction_id);
ALTER TABLE portfolios ADD CONSTRAINT uq_user_market UNIQUE (user_id, market);
ALTER TABLE users ADD CONSTRAINT UK9q63snka3mdh91as4io72espi UNIQUE (phone_number);

-- 외래 키 (이름은 Hibernate가 만든 그대로). payment_events.user_id 는 일부러 외래 키가 없다(원장을 회원과 느슨하게 둔다)
ALTER TABLE category_spare_change_rules ADD CONSTRAINT FKt37rc31r9jtyh81xlfq3mx1ua FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE device_tokens ADD CONSTRAINT FKhc7d11bnr8x9gs5biohdhnx1c FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE investment_profile ADD CONSTRAINT FK49ohyrdluxpy0inbyvs09dqyv FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE investment_profile_themes ADD CONSTRAINT FKnb0eta7kgbh9nqw2v618d4ct8 FOREIGN KEY (investment_profile_id) REFERENCES investment_profile (id);
ALTER TABLE linked_accounts ADD CONSTRAINT FKmsnsia7gyeipkp0to1ftol5wj FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE payment_events ADD CONSTRAINT FKd8sqlp6xbq3jq9yfo2p0lkclh FOREIGN KEY (target_coin_market) REFERENCES coin_metadata (market);
ALTER TABLE portfolios ADD CONSTRAINT FK9xt36kgm9cxsf79r2me0d9f6u FOREIGN KEY (user_id) REFERENCES users (id);
