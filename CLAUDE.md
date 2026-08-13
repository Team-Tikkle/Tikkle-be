# CLAUDE.md

Guidance for Claude working in the **Tikkle backend** repository.

**Scope of this file.** Everything from `RULES.md` and `AGENTS.md` (architecture, coding conventions, logging, working method, design system) is absorbed here — Claude does **not** need to open those two files.

**Still read `docs/` when you need domain detail.** The `docs/` folder is the source of truth for the product plan, requirements, the payment pipeline, DB schema, and screens; go there rather than duplicating it:
- `docs/Tikkle_plan.md` — business/service plan, architecture rationale, roadmap (top-level planning doc)
- `docs/Tikkle_requirements.md` — screen-by-screen features + `FEAT-SYS-*` engine specs (happy path, exceptions, data hints)
- `docs/Tikkle_payment_investment_flow.md` — the payment→investment pipeline end to end: `PaymentStatus` state model, approve/reject/SSE contract, deposit & trade polling, every failure case and where the user's KRW ends up
- `docs/Tikkle_ERD.md` — full DDL, table constraints, relations, Redis key catalog, and every enum value (synced to entity code)
- `docs/화면설명서/화면설명서.md` — every app screen (IDs, UI components, behaviour) with screenshots

**Client-facing API contracts are not kept in `docs/`.** Swagger (`/swagger-ui/index.html`) is the API reference; the request/response shapes live in the `{Domain}Swagger` interfaces.

**A change to the payment pipeline, a `PaymentStatus` transition, or an SSE event means `docs/Tikkle_payment_investment_flow.md` must change in the same PR.** Likewise, an entity/enum change means `docs/Tikkle_ERD.md` changes with it.

Sections 8 and 11 below are quick orientation only; `docs/` has the authoritative detail.

---

## 1. What Tikkle Is

Tikkle is a Spring Boot micro-investing backend for 2030 first-jobbers. It rounds up the spare change from a user's card payments and invests it into crypto (via Upbit), matched to each user's risk profile and settings.

Value proposition: connect everyday spending to investing with no psychological barrier, using an "AI proxy" that represents the user's 5-axis crypto profile to recommend and trade coins.

End-to-end flow: SMS (phone + password) auth → setup via `/api/settings/*` (5-axis risk profile, per-category spare-change rules, target card, Upbit API key) → Android app scrapes payment push notifications and sends them HMAC-signed → fail-fast filtering (signature, timestamp, dedupe, target-card match) → 2-tier AI merchant categorization (7 categories) → dynamic spare-change calculation → 2-stage quant engine picks the target coin → user approves the buy → Upbit KRW deposit (2FA) → buy order → portfolio & insight views.

**Every buy is user-approved.** There is no auto/manual mode — the concept was removed, and the terms "execution mode" / AUTO / MANUAL do not appear anywhere in the codebase. Don't reintroduce them.

- Prod API: `https://api.tikkle.xyz` · Swagger: `https://api.tikkle.xyz/swagger-ui/index.html`
- Local Swagger: `http://localhost:8080/swagger-ui/index.html`

## 2. Tech Stack

| Area | Choice |
| --- | --- |
| Language | **Java 21** |
| Framework | **Spring Boot 4.0.6** |
| Persistence | Spring Data JPA + MySQL 8 (complex queries use `@Query` JPQL — **QueryDSL is not a dependency**) |
| Cache / Token store | Redis 7 |
| Security | Spring Security, JWT (jjwt for app auth, auth0 java-jwt for Upbit), BCrypt, HMAC SHA-256 request signing, AES-256 field encryption |
| AI | Spring AI (`spring-ai-bom:2.0.0`) — Google Gemini `gemini-2.5-flash` (sync merchant classification) + DeepSeek `deepseek-v4-pro` via the OpenAI-compatible starter, `base-url: https://api.deepseek.com` (12h universe generation) |
| External APIs | Upbit, CoinGecko, Fear & Greed, Google News RSS (rome), CoolSMS (Nurigo SDK) |
| Push | Firebase Admin SDK (FCM) — server-initiated payment result notifications; gated by `tikkle.fcm.enabled` (off locally, no-op when disabled) |
| Realtime | Spring WebFlux WebClient (AI streaming), SSE (payment domain) |
| Build | Gradle wrapper, `jar { enabled = false }` |
| Docs | springdoc-openapi / Swagger |

There are **no test classes yet** (only `src/test/resources/application-test.yml` exists; zero test sources). Verification is compile-based (see §12).

## 3. Build, Run, Verify

Use the Gradle wrapper (`./gradlew`, or `.\gradlew.bat` on Windows PowerShell). The sandbox may lack JDK 21 or DB/Redis — commands are for the user's environment / reference.

```bash
# Compile check — the PRIMARY verification step (no DB/Redis needed)
./gradlew compileJava

# Full build (compiles main; there are no tests to run)
./gradlew build

# Run locally (needs MySQL + Redis up)
./gradlew bootRun --args='--spring.profiles.active=local'

# Local infra: MySQL localhost:3306 / db tikkle_db, Redis localhost:6379
docker-compose -f docker-compose.local.yml up -d
```

**Config profiles:** `application.yml` only selects the active profile (`local`). Real config lives in `application-local.yml` (top-level keys: `spring`, `jwt`, `tikkle`, `app`, `coolsms`) and `application-prod.yml`. **`application-prod.yml` and `.env` are gitignored — never commit secrets, keys, or credentials.** Timezone is forced to KST at the very top of the main class.

## 4. Package / Domain Layout

Root package: `com.tikkle`. Feature-first, layered per domain.

```
com.tikkle
├── auth        # SMS signup/login (CoolSMS), JWT issue/verify, refresh tokens, password reset, local-only test tokens
├── global      # config, security (JWT/CORS), exception, ApiResponse wrapper, AES256 crypto util
├── insight     # investment terms/articles/videos (seeded), Google News RSS fetch, market topics
├── investment  # 2-stage AI recommendation, coin metadata, 5-axis risk profile, portfolio entity (no controller — reads go through `upbit`)
├── notice      # 공지사항 list/detail for the settings screen (read-only; rows are inserted straight into the DB, no admin page)
├── notification # FCM device-token register/unregister + push result-notification sending (payment pipeline results)
├── payment     # payment push ingestion, fail-fast filter, spare-change calc, order approve/reject, deposit/trade polling, SSE
├── settings    # spare-change rules, investment profile, target card, Upbit key, investment on/off
├── upbit       # Upbit market/ticker/order/deposit/account API integration + realtime portfolio read
└── user        # user read, withdraw
```

Each domain follows: `controller`, `service`, `repository`, `entity` (+`entity/enums`), `dto/request`, `dto/response`, `exception`, `swagger`. Some add `scheduler`, `client`, `filter`, `interceptor`, `sse`, `fetcher`, `seed`, `config`, `util`, `service/component`. `investment` has no `controller`/`swagger`; `notification` has no `exception` (token validation falls back to `COMMON-002`); `notice` has no `dto/request` (read-only domain); `global` has no domain layers.

**There is no `onboarding` domain.** First-time setup is done through the `settings` endpoints.

## 5. Architecture Rules (Controller → Service → Repository)

- **Controller** — only receives HTTP requests / returns responses. No business logic. Does DTO binding + `@Valid`. Delegates everything to Service. **No logging in controllers.**
- **Service** — all business logic. Class-level `@Transactional(readOnly = true)`; add method-level `@Transactional` on write methods. Log meaningful business events here (skip simple reads).
- **Repository** — Spring Data JPA interfaces. For queries beyond derived method names, use `@Query` with JPQL (see `PaymentEventRepository`). QueryDSL is **not** on the dependency list — don't reach for it.

### Adding a new domain
Mirror the `user` domain and create/update: (1) `{Domain}Swagger.java` interface in the domain's `swagger` package, implemented by the controller; (2) new entries in `global.exception.ErrorCode` (grouped by domain with a comment + code like `PAYMENT-001`); (3) a `CustomException` subclass only if you need to carry extra data, plus a matching handler in `GlobalExceptionHandler`; (4) the standard package structure above.

## 6. Core Conventions (follow exactly)

**API responses** — Never return `ResponseEntity` from controllers. Always return `com.tikkle.global.response.ApiResponse<T>`:
```java
return ApiResponse.success(data);        // 200 with data (code "SUCCESS", "요청에 성공했습니다.")
return ApiResponse.success(message, data); // 200, custom message
return ApiResponse.successWithNoData();  // 200, no data (data omitted via @JsonInclude NON_NULL)
```
Default status is 200; use `@ResponseStatus(HttpStatus.CREATED)` etc. for others. `ApiResponse` JSON order is `{code, message, data}`. (The one intended exception: `GlobalExceptionHandler` returns `ResponseEntity` to set error HTTP status codes.)

**Exceptions** — Throw a domain `CustomException` subclass tied to an `ErrorCode`; let `GlobalExceptionHandler` (a `@RestControllerAdvice`) catch it. Example: `.orElseThrow(UserNotFoundException::new)`. Add new codes to `ErrorCode` with the `(HttpStatus, "DOMAIN-NNN", "메시지")` shape (see the full catalog in §9). `GlobalExceptionHandler` also maps `@Valid` failures (`MethodArgumentNotValidException` → `COMMON-002` with the first field message), malformed JSON, and unsupported HTTP methods.

**Entities** — No promiscuous `@Setter`. Mutate state through intent-revealing domain methods (`withdraw()`, `updateProfile()`). Construct via `@Builder` on a **constructor** (not class level). Restrict the default constructor: `@NoArgsConstructor(access = AccessLevel.PROTECTED)`. `@Table(name = ...)` names use **UPPER_SNAKE_CASE** (e.g. `PAYMENT_EVENTS`) — every table follows this without exception.

**DTOs** — Strictly separate request/response with `Request`/`Response` suffixes in `dto.request` / `dto.response`. Prefer a static factory `from(Entity ...)` on the DTO for entity→DTO conversion. DTOs and repositories get **no Javadoc**.

**Swagger** — Keep annotations out of controllers. Put them on a `{Domain}Swagger` interface (`@Tag`, `@Operation`, `@ApiResponses` with `@ExampleObject`); the controller `implements` it (e.g. `UserController implements UserSwagger`).

**Dependency injection** — Constructor injection via Lombok `@RequiredArgsConstructor`. Avoid `@Autowired`.

**Auth principal** — Identify the user with `CustomUserDetails.getUserId()` (Long), **not** the email/`getUsername()`.

**No FQCN inlining** — Always `import` a class and use its simple name; never inline full package paths like `com.tikkle.payment.exception.DuplicatePaymentException` (unless a genuine name clash forces it).

**Naming** — Classes/Interfaces `PascalCase`, methods/vars `camelCase`, constants `UPPER_SNAKE_CASE`.

### Canonical templates
```java
// Entity
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "PAYMENT_EVENTS")
public class PaymentEvent {
    // fields...

    @Builder
    private PaymentEvent(Long userId, int amount) {
        this.userId = userId;
        this.amount = amount;
    }

    public void markInvested() { this.status = PaymentStatus.INVESTED; } // intent-revealing mutation
}

// Controller (delegates only, no logging)
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController implements UserSwagger {
    private final UserService userService;

    @Override
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(userService.getMe(userDetails.getUserId()));
    }
}

// Service (business logic + logging + tx)
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;

    @Transactional
    public void withdrawMe(Long userId) {
        findActiveUserById(userId).withdraw();
    }

    private User findActiveUserById(Long userId) {
        return userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(UserNotFoundException::new);
    }
}
```

## 7. Logging & Comments

- **Log messages are written in Korean**, format `[ClassName] event/action - paramName: {}`, e.g. `log.info("[PaymentService] 결제 처리 시작 - transactionId: {}", id);`. Use SLF4J via Lombok `@Slf4j`.
- Log in Service / Interceptor / core business classes; **skip logging in controllers and for simple read-only lookups.**
- **Javadoc (`/** ... */`) is required** on Controller, Service, Filter, Interceptor classes and their key methods. **Omit Javadoc** on Entity, DTO, Repository, Swagger classes. Inline comments are terse (`// 1단계: ...`), no decorative separator lines.

## 8. Database Schema (orientation — full detail in `docs/Tikkle_ERD.md`)

**→ Read `docs/Tikkle_ERD.md` for the full DDL, constraints, relations, and complete enum value lists.** It is kept in sync with entity code. Quick orientation:

- **Tables** (all UPPER_SNAKE_CASE): `USERS`, `INVESTMENT_PROFILE`, `INVESTMENT_PROFILE_THEMES`, `CATEGORY_SPARE_CHANGE_RULES`, `LINKED_ACCOUNTS`, `PAYMENT_EVENTS`, `PAYMENT_CATEGORY_MAPPING`, `AI_RECOMMENDATION_HISTORY`, `PORTFOLIOS`, `COIN_METADATA`, `MARKET_TOPICS`, `INVESTMENT_TERMS`, `BEGINNER_ARTICLES`, `RECOMMENDED_VIDEOS`, `DEVICE_TOKENS`.
- **Relations gotcha:** `INVESTMENT_PROFILE`, `CATEGORY_SPARE_CHANGE_RULES`, `LINKED_ACCOUNTS`, `PORTFOLIOS`, `DEVICE_TOKENS` all hold a JPA `@ManyToOne`/`@OneToOne` FK to `USERS` (so withdrawal must delete them before `USERS`). The exception is `PAYMENT_EVENTS.user_id`, which is a **plain Long scalar with no JPA relation (loose coupling)** — the payment ledger stays independent of the user entity. `PAYMENT_EVENTS.target_coin_market` is a JPA FK → `COIN_METADATA(market)`. Upbit keys in `LINKED_ACCOUNTS` are AES-256 encrypted.
- **Redis (non-RDB) stores:** refresh tokens (`refresh_token` RedisHash), payment idempotency key (`payment:tx:{transactionId}`, SETNX 24h), user settings cache (`user:settings:{userId}` Hash), SMS auth state (`SMS_AUTH:{purpose}:{phone}` 3min code, `SMS_ATTEMPT:` fail counter, `SMS_COOLDOWN:` 60s resend lock, `SMS_DAILY:` 24h send counter, `SIGNUP_TOKEN:{phone}` / `PASSWORD_RESET_TOKEN:{phone}` 30min). **No distributed scheduler lock exists** — schedulers assume a single instance.
- **Enums** live in each domain's `entity/enums`; the ERD doc lists every value. Most-used: `PaymentCategory` (7: CAFE/MART/FOOD/SHOPPING/TRAFFIC/CULTURE/ETC), `PaymentStatus` (NOT_INVESTED/PENDING_PURCHASE/PENDING_DEPOSIT/PENDING_TRADE/INVESTED/FAILED), and the 5-axis profile (RiskTolerance, TrendSensitivity, DiversificationType, MemeAcceptance, CryptoTheme). The 9 Risk×Trend combos drive `AI_RECOMMENDATION_HISTORY.profile_hash_key` (e.g. `BUY_MORE:FULL_TREND`).

## 9. ErrorCode Catalog

Grouped by domain in `global.exception.ErrorCode`. When adding codes, follow the next number in the domain group.

- **COMMON**: `COMMON-001` internal error (500), `COMMON-002` invalid input (400), `COMMON-003` method not allowed (405), `COMMON-004` URL not found (404), `COMMON-005` file size exceeded (413)
- **AUTH**: `AUTH-001` unauthorized (401), `AUTH-002` invalid token (401), `AUTH-003` expired token (401), `AUTH-004` access denied (403), `AUTH-006` refresh token expired (401), `AUTH-007` phone already registered (409), `AUTH-008` invalid password (401) — *`AUTH-005` was `INVALID_SOCIAL_TOKEN`, retired when Google OAuth2 login was removed in favour of SMS auth (#121); leave the number retired rather than reusing it, same as `USER-002`*
- **SMS**: `SMS-001` send failed (500), `SMS-002` invalid/expired code (400), `SMS-003` invalid/expired verification token (400) — `INVALID_VERIFICATION_TOKEN`, covers **both** the signup token and the password-reset token, and covers both "expired" and "mismatched", `SMS-004` resend cooldown (429), `SMS-005` daily send limit (429), `SMS-006` verify attempts exceeded (429)
- **USER**: `USER-001` not found (404), `USER-003` linked account not found (404), `USER-004` invalid 2FA provider (400), `USER-005` no category rule (404) — *`USER-002` was removed when withdrawal became a hard delete; leave the number retired rather than reusing it*
- **PAYMENT**: `PAYMENT-001` invalid signature (401), `-002` expired timestamp (401), `-003` invalid AI response (500), `-004` event not found (404), `-005` unknown status (500), `-006` invalid status (400), `-007` Upbit trade failed (500), `-008` card mismatch (409), `-009` duplicate payment (409), `-010` body-caching filter missing (500), `-011` investment disabled (409)
- **INVESTMENT**: `-001` AI recommendation failed (500), `-003` coin not found (404), `-004` coin sync failed (500), `-005` profile not found (404)
- **UPBIT**: `-001`..`-014` cover order/inquiry/token/market/ticker/auth-param/deposit/invalid-key/candle/account/api-call/cancel failures (mostly 500; `-010` invalid key is 401)
- **INSIGHT**: `INSIGHT-001` article not found (404)
- **NOTICE**: `NOTICE-001` notice not found (404) — also returned when the notice exists but `is_visible = false`
- **SECURITY**: `SECURITY-001` encryption/decryption failed (500), `SECURITY-002` invalid encryption key (500)

## 10. API Surface

| Domain | Method / Path | Purpose | Auth |
| --- | --- | --- | --- |
| Auth | `POST /api/auth/signup` | SMS-verified signup (phone + password) | Public |
| Auth | `POST /api/auth/login` | phone + password login | Public |
| Auth | `POST /api/auth/reissue` | reissue JWT from refresh token | Public |
| Auth | `POST /api/auth/logout` | delete refresh token | JWT |
| Auth | `POST /api/auth/sms/send` `.../verify` | signup SMS verification | Public |
| Auth | `POST /api/auth/password/reset-sms/send` `.../verify` | password-reset SMS verification | Public |
| Auth | `POST /api/auth/password/reset` | reset password with the verify token; also invalidates the refresh token | Public |
| Auth | `POST /api/auth/test-token` `.../test-signup` | local-only test JWT issue | Public (`local` only) |
| User | `GET /api/users/me` | my info | JWT |
| User | `DELETE /api/users/me` | withdraw — **hard delete** of the user + all owned data (profile, rules, linked account, portfolios, payment ledger, device tokens) + Redis session/cache. The phone number is freed immediately, so re-signup is allowed right away | JWT |
| Notification | `POST /api/users/me/device-token` | register FCM device token (idempotent upsert; transfers ownership on re-login) | JWT |
| Notification | `DELETE /api/users/me/device-token` | unregister FCM device token on logout (idempotent) | JWT |
| Portfolio | `GET /api/upbit/portfolios` | realtime holdings via Upbit API + WS market codes | JWT |
| Payment | `POST /api/payments` | Android payment push ingestion | **HMAC** (permitAll in security, verified by filter/interceptor) |
| Payment | `GET /api/payments/dashboard` | monthly payment/spare-change dashboard | JWT |
| Payment | `GET /api/payments` | paged payment feed | JWT |
| Payment | `GET /api/payments/in-progress` | in-flight events (`PENDING_DEPOSIT`/`PENDING_TRADE`) so the app can restore the screen and re-subscribe SSE after leaving for 2FA | JWT |
| Payment | `PATCH /api/payments/{id}/category` | correct a payment's category | JWT |
| Order | `POST /api/payments/{eventId}/approve` | approve the buy → triggers Upbit KRW deposit (2FA) | JWT |
| Order | `POST /api/payments/{eventId}/reject` | reject the buy → NOT_INVESTED | JWT |
| Order | `GET /api/payments/{eventId}/stream` | SSE stream of the buy pipeline result; re-subscribable, replays the current state once on connect | JWT |
| Settings | `GET /api/settings` | rules + profile + linked account + investment on/off | JWT |
| Settings | `PATCH /api/settings/spare-change-rules` | change per-category rules | JWT |
| Settings | `PATCH /api/settings/profile` | change 5-axis investment profile | JWT |
| Settings | `PATCH /api/settings/kbank` | change target card info | JWT |
| Settings | `PATCH /api/settings/upbit` | replace Upbit key + 2FA provider (validated on write) | JWT |
| Settings | `PATCH /api/settings/investment` | turn spare-change investing on/off | JWT |
| Insight | `GET /api/insights/market-topics` | latest market topics | JWT |
| Insight | `GET /api/insights/terms` | investment terms | JWT |
| Insight | `GET /api/insights/articles` `.../{id}` | beginner articles list / detail | JWT |
| Insight | `GET /api/insights/videos` | recommended videos | JWT |
| Notice | `GET /api/notices` | 공지사항 list (visible only; pinned first, then newest `publishedAt`) | JWT |
| Notice | `GET /api/notices/{id}` | 공지사항 detail (hidden notices 404 even by id) | JWT |

Security config: STATELESS, CSRF off, CORS on, JWT filter before `UsernamePasswordAuthenticationFilter`. `PERMIT_ALL_URLS` = `/api/auth/reissue`, `/api/auth/signup`, `/api/auth/login`, `/api/auth/sms/**`, `/api/auth/password/reset-sms/send`, `/api/auth/password/reset-sms/verify`, `/api/auth/password/reset`, `/swagger-ui/**`, `/v3/api-docs/**`; plus `POST /api/payments`. **Only in `local` profile**: `/api/auth/test-token`, `/api/auth/test-signup`, `/api/test/**` are public. (The three `password/**` entries are load-bearing: a user who forgot their password has no JWT, so requiring one made the reset flow unreachable.)

> **SMS abuse guards** (all Redis-backed, in `SmsService`): 60s resend cooldown + 5 sends per 24h per phone+purpose (`SMS-004`/`SMS-005`); 5 wrong-code attempts invalidate the code (`SMS-006`). `sendPasswordResetSms` returns 200 for unregistered numbers (and still burns the quota) to prevent user enumeration.

## 11. Engine Flows & Schedulers (orientation — full specs in `docs/Tikkle_requirements.md`)

**→ Read `docs/Tikkle_requirements.md` (`FEAT-SYS-*`) and `docs/Tikkle_plan.md` for the authoritative pipeline specs, happy paths, and exception handling.** Quick map of the core payment pipeline:

1. **Security verify** (FEAT-SYS-001) — timestamp + HMAC SHA-256 signature; reject >5min old (`PAYMENT-002`) or bad signature (`PAYMENT-001`). Needs the request-body-caching filter (`PAYMENT-010` if missing).
2. **Fail-fast filtering** (FEAT-SYS-002) — dedupe by `transactionId` via Redis SETNX (`PAYMENT-009`), investing disabled check (`PAYMENT-011`), target-card match (`PAYMENT-008`). All three read the `user:settings:{userId}` Redis hash and throw **before** any ledger insert.
3. **2-tier categorization + spare-change** (FEAT-SYS-003) — keyword dictionary (`PAYMENT_CATEGORY_MAPPING`) HIT, else a **synchronous** Gemini call whose result is written back to the dictionary; apply the user's rule; **0원 or < 5,100원 → `NOT_INVESTED` + ledger insert + `IGNORE_*` action type** (early exit).
4. **Target coin** (FEAT-SYS-006) — 2-stage quant engine: Stage 1 (12h AI universe of 15 per Risk×Trend combo) + Stage 2 (payment-time scoring over live price, theme prefs, meme acceptance, buy history); ~3s latency budget. Result saved as `PENDING_PURCHASE` and returned to the app, which raises its own local notification.
5. **Approve → deposit → trade** (FEAT-SYS-004/005) — `POST /approve` requests an Upbit **KRW deposit with 2FA** (`two_factor_provider`) → `PENDING_DEPOSIT` → `UpbitDepositPollingScheduler` (3s) sees the deposit land and places the buy order → `PENDING_TRADE` → `UpbitTradePollingScheduler` (10s) sees the fill → `INVESTED` + portfolio update + SSE. Unapproved for 24h → `NOT_INVESTED`. Upbit 401 anywhere → `FAILED` + `UPBIT_INVALID_KEY` SSE event.

> **The 2FA step happens in another app** (KakaoTalk/Naver/Hana), so leaving Tikkle during `PENDING_DEPOSIT` is the normal path and the SSE connection always drops. Don't treat a dropped stream as an error: the app re-subscribes via `GET /api/payments/in-progress` → `/stream`, and results that land while disconnected are delivered by FCM. `PaymentViewStatus.IN_PROGRESS` (not `PENDING`) marks already-approved events so the app doesn't offer the approve button again.
>
> `POST /api/payments` never calls Upbit — the first Upbit call for a payment happens at `/approve`.

**Schedulers** (no distributed lock — single instance assumed). `SchedulingConfig` provides a dedicated `TaskScheduler` (pool 5, `tikkle-sched-*`) because Spring's default pool size of 1 lets one slow job stall every other scheduler — long jobs (`AiPortfolioScheduler`, `MarketTopicScheduler` startup run) are additionally `@Async` so they release the scheduler thread immediately:
- `CoinSyncScheduler` — 04:00 KST — sync Upbit coin metadata
- `AiPortfolioScheduler` — 02:00 / 14:00 KST — generate AI recommendation candidates per profile combo
- `MarketTopicScheduler` — 07:00 / 18:00 KST — collect market topics from Google News RSS
- `PendingOrderExpirationScheduler` — hourly — `PENDING_PURCHASE` older than 24h → `NOT_INVESTED`
- `UpbitDepositPollingScheduler` — every 3s — poll `PENDING_DEPOSIT`, place the buy order on arrival
- `UpbitTradePollingScheduler` — every 10s — poll `PENDING_TRADE`, settle fills (10min timeout → cancel)

## 12. Working Method (how Claude operates here)

1. **Clarify first.** If a requirement is ambiguous (nullability, which HTTP status, 400 vs 404, etc.), ask before implementing — don't guess.
2. **Read before writing.** Search existing Entity / DTO / Repository / Service for reuse before adding anything new; match established patterns.
3. **Don't touch unrequested code.** Never refactor or delete code the user didn't ask about — propose it and get agreement first.
4. **Deliver complete units.** Provide full, immediately-usable methods/classes, not fragments.
5. **Verify by compiling.** After changes, run `./gradlew compileJava` (or `build`). There is no test suite; do not claim tests pass. If you add tests, wire up the `src/test` set — JUnit Platform is already configured.

## 13. Git / PR Conventions

- **Branches:** `feature/#<issue>-<slug>`, `fix/#<issue>-<slug>`, `refactor/#<issue>-<slug>`, `chore/#<issue>-<slug>`.
- **Commits:** `[#<issue>]<type>: <설명 in Korean>` — e.g. `[#135]feat: 휴대폰 SMS 인증을 활용한 비밀번호 재설정 API 구현`. Types in use: `feat`, `fix`, `refactor`, `chore`.
- PRs use `.github/PULL_REQUEST_TEMPLATE.md` (관련 이슈 / 작업 내용 / Checklist). Reference the issue; use `closed #N` to auto-close.

## 14. Design System (only for UI / slides / HTML deliverables)

Flat, minimal Toss/Upbit style. Backgrounds white `#FFFFFF` / light-grey `#F2F4F6`; brand blue `#3D54C5`, point blue `#3182F6`; `Pretendard` font; generous whitespace with a subtle ambient blue radial glow. Keep slide headers identical across slides (`.content { max-width: 1040px; }`, `.header { margin-bottom: 40px; }`) to avoid jumping. Tikkle logo: separated horizontal bar + upward zigzag arrow inside a blue rounded square, dot at lower-right.
