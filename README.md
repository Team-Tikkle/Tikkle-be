# Tikkle Backend

Tikkle은 카드 결제에서 발생하는 잔돈을 사용자의 투자 성향과 설정에 맞춰 가상자산 매수로 연결하는 백엔드 서비스입니다. 결제 알림 수신, 잔돈 규칙 계산, AI 기반 가맹점 분류, Upbit 주문 연동, 포트폴리오 조회, 투자 인사이트 제공 기능을 포함합니다.

- 운영 서버: `https://api.tikkle.xyz`
- Swagger UI: `https://api.tikkle.xyz/swagger-ui/index.html`
- 로컬 Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 4.0.6 |
| Persistence | Spring Data JPA, MySQL |
| Cache / Token Store | Redis |
| Security | Spring Security, JWT, HMAC SHA-256 요청 서명 |
| AI | Spring AI, Google Gemini, Anthropic Claude |
| External API | Google OAuth, Upbit API, CoinGecko, Fear & Greed API, Google News RSS |
| API Docs | springdoc-openapi / Swagger |
| Infra | Docker, Docker Compose, GitHub Actions, GCP Artifact Registry |

## 주요 기능

- Google OAuth 로그인 및 자체 JWT 발급
- 사용자 온보딩: 투자 성향, 관심 테마, 매매 방식, 잔돈 규칙, Upbit API 키 등록
- 결제 푸시 알림 수신 및 HMAC 서명 검증
- 결제 가맹점 AI 분류 및 7대 카테고리 매핑
- 카테고리별 잔돈 규칙 계산
- 자동 매수 또는 수동 승인 기반 Upbit 주문 처리
- 포트폴리오 스냅샷 조회
- 결제 대시보드 및 결제 피드 조회
- 투자 설정 변경 및 거래소 계정 정보 교체
- 투자 용어, 초보자 글, 추천 영상, 마켓 토픽 제공
- 스케줄러 기반 코인 메타데이터 동기화, AI 추천 후보 생성, 뉴스 수집, 수동 주문 만료 처리

## 프로젝트 구조

```text
src/main/java/com/tikkle
├── auth        # Google OAuth, JWT 발급/검증, refresh token 관리
├── global      # 공통 설정, 보안, CORS, 예외 처리, 응답 포맷, 암호화 유틸
├── insight     # 투자 콘텐츠, Google News RSS 수집, 마켓 토픽
├── investment  # 포트폴리오, AI 추천, 코인 메타데이터, 투자 성향
├── onboarding  # 최초 사용자 설정 등록
├── payment     # 결제 알림 수신, 잔돈 계산, 결제 내역, 수동 주문 승인/거절
├── settings    # 매매 방식, 잔돈 규칙, 거래소 계정 설정
├── upbit       # Upbit 마켓/시세/주문 API 연동
└── user        # 사용자 조회, 수정, 탈퇴
```

## API 요약

| Domain | Method / Path | 설명 | 인증 |
| --- | --- | --- | --- |
| Auth | `POST /api/auth/oauth/google` | Google access token으로 로그인/회원가입 | Public |
| Auth | `POST /api/auth/reissue` | refresh token으로 JWT 재발급 | Public |
| Auth | `POST /api/auth/logout` | refresh token 삭제 | JWT |
| Onboarding | `POST /api/onboarding` | 투자 성향, 잔돈 규칙, Upbit 키 등록 | JWT |
| User | `GET /api/users/me` | 내 정보 조회 | JWT |
| User | `PATCH /api/users/me` | 내 정보 수정 | JWT |
| User | `DELETE /api/users/me` | 회원 탈퇴 | JWT |
| Portfolio | `GET /api/portfolios` | 보유 코인 및 평가 금액 스냅샷 | JWT |
| Payment | `POST /api/payments` | Android 결제 푸시 알림 수신 | HMAC |
| Payment | `GET /api/payments/dashboard` | 월별 결제/잔돈 대시보드 | JWT |
| Payment | `GET /api/payments` | 결제 피드 페이징 조회 | JWT |
| Manual Order | `POST /api/payments/{eventId}/approve` | 수동 매수 승인 | JWT |
| Manual Order | `POST /api/payments/{eventId}/reject` | 수동 매수 거절 | JWT |
| Settings | `GET /api/settings` | 매매 방식과 잔돈 규칙 조회 | JWT |
| Settings | `PATCH /api/settings/execution-mode` | 자동/수동 매매 방식 변경 | JWT |
| Settings | `PATCH /api/settings/spare-change-rules` | 카테고리별 잔돈 규칙 변경 | JWT |
| Settings | `PATCH /api/settings/linked-account` | Upbit API 키 및 카드 정보 교체 | JWT |
| Insight | `GET /api/insights/market-topics` | 최신 마켓 토픽 조회 | JWT |
| Insight | `GET /api/insights/terms` | 투자 용어 조회 | JWT |
| Insight | `GET /api/insights/articles` | 초보자 글 목록 조회 | JWT |
| Insight | `GET /api/insights/articles/{id}` | 초보자 글 상세 조회 | JWT |
| Insight | `GET /api/insights/videos` | 추천 영상 조회 | JWT |

`local` 프로필에서만 테스트용 API인 `/api/auth/test-token`, `/api/auth/test-signup`, `/api/test/**`가 공개됩니다.

## 로컬 실행

### 사전 준비

- Java 21
- Docker / Docker Compose
- MySQL 8.0
- Redis 7

### 인프라 실행

```bash
docker-compose -f docker-compose.local.yml up -d
```

로컬 compose는 MySQL과 Redis를 실행합니다.

- MySQL: `localhost:3306`
- Database: `tikkle_db`
- Redis: `localhost:6379`

### 애플리케이션 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

### 테스트

```bash
./gradlew test
```

## 주요 스케줄러

| Scheduler | 주기 | 설명 |
| --- | --- | --- |
| `CoinSyncScheduler` | 매일 04:00 KST | Upbit 코인 메타데이터 동기화 |
| `AiPortfolioScheduler` | 매일 00:00, 12:00 KST | 투자 성향별 AI 추천 후보 생성 |
| `MarketTopicScheduler` | 매일 07:00, 18:00 KST | Google News RSS 기반 마켓 토픽 수집 |
| `ManualOrderExpirationScheduler` | 매시간 정각 | 수동 승인 대기 주문 만료 처리 |

## 참고 문서

- DB 스키마: `docs/Tikkle_ERD.md`
- 기능 명세: `docs/Tikkle_requirements.md`
- 개발 계획: `docs/Tikkle_plan.md`
