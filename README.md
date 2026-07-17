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
| Persistence | Spring Data JPA, MySQL 8 (복잡 쿼리는 QueryDSL) |
| Cache / Token Store | Redis 7 |
| Security | Spring Security, JWT, BCrypt, HMAC SHA-256 요청 서명, AES-256 필드 암호화 |
| AI | Spring AI — Google Gemini `gemini-2.5-flash` (가맹점 분류), DeepSeek `deepseek-v4-pro` (AI 유니버스 생성, OpenAI 호환 스타터 사용) |
| External API | Upbit API, CoinGecko, Fear & Greed API, Google News RSS, CoolSMS(Nurigo) |
| Realtime | Spring WebFlux WebClient, SSE |
| API Docs | springdoc-openapi / Swagger |
| Infra | Docker, Docker Compose, GitHub Actions, GCP Artifact Registry |

## 주요 기능

- 휴대폰 SMS 본인인증 기반 회원가입 / 로그인 및 자체 JWT 발급, SMS 인증 기반 비밀번호 재설정
- 사용자 설정: 5축 투자 성향, 카테고리별 잔돈 규칙, 타겟 카드, Upbit API 키 등록
- 결제 푸시 알림 수신 및 HMAC 서명 검증
- 결제 가맹점 AI 분류 및 7대 카테고리 매핑
- 카테고리별 잔돈 규칙 계산
- 2단계 퀀트 엔진 기반 매수 종목 선정
- 사용자 승인 기반 Upbit 원화 입금(2FA) 및 매수 주문 처리
- 포트폴리오 실시간 조회
- 결제 대시보드 및 결제 피드 조회, 결제 카테고리 수정
- 투자 설정 변경 및 거래소 계정 정보 교체
- 투자 용어, 초보자 글, 추천 영상, 마켓 토픽 제공
- 스케줄러 기반 코인 메타데이터 동기화, AI 추천 후보 생성, 뉴스 수집, 미승인 주문 만료, 입금·체결 폴링

> **모든 매수는 사용자 승인을 거칩니다.**

## 프로젝트 구조

```text
src/main/java/com/tikkle
├── auth        # SMS 회원가입/로그인, JWT 발급/검증, refresh token, 비밀번호 재설정
├── global      # 공통 설정, 보안, CORS, 예외 처리, 응답 포맷, 암호화 유틸
├── insight     # 투자 콘텐츠, Google News RSS 수집, 마켓 토픽
├── investment  # AI 추천, 코인 메타데이터, 5축 투자 성향, 포트폴리오 엔티티
├── payment     # 결제 알림 수신, 잔돈 계산, 결제 내역, 주문 승인/거절, 입금·체결 폴링, SSE
├── settings    # 잔돈 규칙, 투자 성향, 타겟 카드, Upbit 키, 투자 on/off
├── upbit       # Upbit 마켓/시세/주문/입금/계좌 API 연동 및 실시간 포트폴리오 조회
└── user        # 사용자 조회, 탈퇴
```

최초 사용자 설정은 별도 도메인이 아니라 `settings` 엔드포인트를 통해 이루어집니다.

## API 요약

| Domain | Method / Path | 설명 | 인증 |
| --- | --- | --- | --- |
| Auth | `POST /api/auth/signup` | SMS 인증 완료 후 회원가입 | Public |
| Auth | `POST /api/auth/login` | 휴대폰 번호 + 비밀번호 로그인 | Public |
| Auth | `POST /api/auth/reissue` | refresh token으로 JWT 재발급 | Public |
| Auth | `POST /api/auth/logout` | refresh token 삭제 | JWT |
| Auth | `POST /api/auth/sms/send` | 회원가입용 인증번호 발송 | Public |
| Auth | `POST /api/auth/sms/verify` | 회원가입용 인증번호 검증 및 임시 토큰 발급 | Public |
| Auth | `POST /api/auth/password/reset-sms/send` | 비밀번호 재설정용 인증번호 발송 | Public |
| Auth | `POST /api/auth/password/reset-sms/verify` | 재설정용 인증번호 검증 및 임시 토큰 발급 | Public |
| Auth | `POST /api/auth/password/reset` | 임시 토큰으로 비밀번호 재설정 (기존 세션 무효화) | Public |
| User | `GET /api/users/me` | 내 정보 조회 | JWT |
| User | `DELETE /api/users/me` | 회원 탈퇴 (완전 삭제, 즉시 재가입 가능) | JWT |
| Portfolio | `GET /api/upbit/portfolios` | Upbit API 기반 실시간 보유 종목 조회 | JWT |
| Payment | `POST /api/payments` | Android 결제 푸시 알림 수신 | HMAC |
| Payment | `GET /api/payments/dashboard` | 월별 결제/잔돈 대시보드 | JWT |
| Payment | `GET /api/payments` | 결제 피드 페이징 조회 | JWT |
| Payment | `PATCH /api/payments/{id}/category` | 결제 카테고리 수정 | JWT |
| Order | `POST /api/payments/{eventId}/approve` | 매수 승인 → Upbit 원화 입금(2FA) 요청 | JWT |
| Order | `POST /api/payments/{eventId}/reject` | 매수 거절 | JWT |
| Order | `GET /api/payments/{eventId}/stream` | 매수 파이프라인 결과 SSE 구독 | JWT |
| Settings | `GET /api/settings` | 잔돈 규칙 + 투자 성향 + 연동 계좌 + 투자 on/off 조회 | JWT |
| Settings | `PATCH /api/settings/spare-change-rules` | 카테고리별 잔돈 규칙 변경 | JWT |
| Settings | `PATCH /api/settings/profile` | 5축 투자 성향 변경 | JWT |
| Settings | `PATCH /api/settings/kbank` | 타겟 카드 정보 변경 | JWT |
| Settings | `PATCH /api/settings/upbit` | Upbit API 키 및 2차 인증 수단 교체 | JWT |
| Settings | `PATCH /api/settings/investment` | 잔돈 투자 on/off | JWT |
| Insight | `GET /api/insights/market-topics` | 최신 마켓 토픽 조회 | JWT |
| Insight | `GET /api/insights/terms` | 투자 용어 조회 | JWT |
| Insight | `GET /api/insights/articles` | 초보자 글 목록 조회 | JWT |
| Insight | `GET /api/insights/articles/{id}` | 초보자 글 상세 조회 | JWT |
| Insight | `GET /api/insights/videos` | 추천 영상 조회 | JWT |

`local` 프로필에서만 테스트용 API인 `/api/auth/test-token`, `/api/auth/test-signup`, `/api/test/**`가 공개됩니다.


## 주요 스케줄러

분산 락이 없으므로 단일 인스턴스 운영을 전제합니다.

| Scheduler | 주기 | 설명 |
| --- | --- | --- |
| `CoinSyncScheduler` | 매일 04:00 KST | Upbit 코인 메타데이터 동기화 |
| `AiPortfolioScheduler` | 매일 02:00, 14:00 KST | 투자 성향별 AI 추천 후보 생성 |
| `MarketTopicScheduler` | 매일 07:00, 18:00 KST | Google News RSS 기반 마켓 토픽 수집 |
| `PendingOrderExpirationScheduler` | 매시간 정각 | 24시간 미승인 매수 대기 건 만료 처리 |
| `UpbitDepositPollingScheduler` | 3초 간격 | 원화 입금 도착 확인 후 매수 주문 실행 |
| `UpbitTradePollingScheduler` | 10초 간격 | 매수 체결 확인 및 정산 (10분 초과 시 취소) |

## 참고 문서

- DB 스키마: `docs/Tikkle_ERD.md`
- 기능 명세: `docs/Tikkle_requirements.md`
- 개발 계획: `docs/Tikkle_plan.md`
- 인증 API 명세 (클라이언트 계약): `docs/Tikkle_auth_api.md`
- 업비트/결제 API 명세 (클라이언트 계약): `docs/Tikkle_upbit_api.md`
