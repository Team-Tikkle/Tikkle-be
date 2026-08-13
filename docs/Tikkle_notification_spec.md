# Tikkle 알림 아키텍처 명세 및 작업 지시서

> **문서 목적** — 티끌의 알림 체계를 정리하고, FCM 부분 도입에 따른 **백엔드 ↔ 프론트엔드 계약**과 양측 작업 범위를 확정한다.
> **대상** — 프론트엔드(Tikkle-app) / 백엔드(Tikkle-be) 공용
> **작성 기준일** — 2026-07-22

---

## 1. 요약 (TL;DR)

- **결제 푸시 스크래핑(`NotificationListenerService`)과 매수 제안 로컬 알림은 절대 건드리지 않는다.** FCM으로 대체 불가하거나, 대체 시 오히려 열화된다.
- **FCM은 "서버가 단독으로 사용자에게 말을 걸어야 하는" 구간에만 추가한다.** 체결 완료 / 매수 취소 / 입금·매수 실패 / 2차 인증 시간 초과 / 키 만료 / 24시간 만료 — 총 7종(§7.2).
- 현재 `PaymentReviewView.vue`에 **"체결이 완료되면 푸시 알림으로 안내해 드립니다"** 라고 쓰여 있으나, **이 알림은 존재하지 않는다.** 이번 작업의 1차 목표는 이 약속을 실제로 지키는 것이다.
- SSE는 제거하지 않는다. FCM과 **역할 분담**한다.

---

## 2. 현재 알림 구조

티끌의 알림은 4개 조각으로 구성된다.

| # | 조각 | 위치 | 기술 | 담당 |
| --- | --- | --- | --- | --- |
| **A** | **인바운드** — 케이뱅크 결제 푸시 가로채기 → 파싱 → 서버 전송 | `PaymentNotificationListener.java` | `NotificationListenerService` + HMAC | FE |
| **B** | **아웃바운드** — "잔돈으로 투자할까요?" 매수 제안 | 같은 파일 `notifyForAction()` / `postFeedback()` | `NotificationCompat` **로컬 알림** | FE |
| **C** | **딥링크** — 알림 탭 → 검토 화면 | `AndroidManifest.xml` + `main.ts` | `tikkle://payments/review?...` | FE |
| **D** | **승인 후 진행 통보** | `SseConnectionManager.java` ↔ `PaymentReviewView.vue` | SSE (`eventId → SseEmitter` in-memory Map) | BE + FE |

### 2.1 검증된 사실 (실기기 테스트 완료 — Galaxy S22+)

| 상황 | 스크래핑(A) | 로컬 알림(B) | 비고 |
| --- | --- | --- | --- |
| 앱 실행 중 | ✅ | ✅ | |
| **최근앱 스와이프 종료** | ✅ | ✅ | **프로세스가 죽지 않음.** 시스템이 `BIND_FOREGROUND_SERVICE` 플래그로 리스너를 바인딩하므로 포그라운드 서비스급 우선순위를 받는다. WebView/Vue만 파괴되고 네이티브 서비스는 계속 동작 |
| 재부팅 후 앱 미실행 | ✅ | ✅ | 잠금 해제 시점에 시스템이 리스너 자동 재바인딩 |
| **설정 → 강제 중지** | ❌ | ❌ | `FLAG_STOPPED` 진입. **FCM도 배달되지 않음.** 사용자가 앱을 직접 켜야만 복구 — Android 보안 모델상 우회 불가 |
| 삼성 "사용 안 함 앱 절전" 편입 | ⚠️ | ⚠️ | 며칠간 앱 미실행 시 자동 편입 가능. **실질적으로 유일한 상시 위협** |

> 📌 **스와이프 종료로는 알림이 끊기지 않는다는 것이 실기기에서 확인되었다.** 따라서 포그라운드 서비스(상시 알림) 도입은 불필요하다.

### 2.2 현재 뚫려 있는 구멍

| 이벤트 | 코드 위치 | 현재 통보 수단 |
| --- | --- | --- |
| **지연 체결 완료** | `UpbitTradePollingProcessor.handleSettledOrder()` | ❌ SSE를 보내지만 **FE가 `PENDING_TRADE`에서 이미 `sseAbort()` 함** → 서버 로그에 "활성화된 SSE 커넥션이 없습니다"만 남고 유실 |
| **매수 10분 타임아웃** (원화가 업비트에 잔류) | `UpbitTradePollingProcessor.handleTimeout()` | ❌ **SSE조차 발송하지 않음** |
| **지연 구간 업비트 키 만료** | `UpbitTradePollingProcessor.handleInvalidKeyError()` | ❌ **SSE조차 발송하지 않음** |
| 입금 실패 / 매수 실패 / 키 만료 | `UpbitDepositPollingProcessor` | ⚠️ SSE만 — 앱이 검토 화면에 떠 있을 때만 도달 |
| **24시간 미승인 만료** | `PendingOrderExpirationScheduler` | ❌ 없음 |

---

## 3. 결정 사항

### 3.1 바꾸지 않는 것

| 조각 | 결정 | 근거 |
| --- | --- | --- |
| **A. 결제 푸시 스크래핑** | **현행 유지** | 케이뱅크 알림은 우리 FCM 프로젝트를 거치지 않는다. `NotificationListenerService`가 **유일한 수단**이며 대체재가 존재하지 않는다 |
| **B. 매수 제안 로컬 알림** | **현행 유지** | ① 서버 응답(`POST /api/payments`)을 이미 손에 쥔 시점이라 FCM 왕복은 지연만 추가 ② FCM 알림도 **동일하게 `POST_NOTIFICATIONS` 권한이 필요**하므로 도달률 개선 없음 ③ 미실행 상태에선 FCM이 오히려 배달 불가 |
| **D. 승인 후 3분 실시간 구간(SSE)** | **현행 유지** | 사용자가 화면을 보며 대기하는 구간. `PROCESSING` 하트비트 + 스피너 UX가 FCM보다 우수 |

### 3.2 새로 추가하는 것

| 대상 | 결정 |
| --- | --- |
| **D′. 스케줄러발 지연·종료 이벤트** | **FCM 신규 도입** — SSE가 이미 끊긴 뒤 발생하거나, 사용자가 앱을 떠난 뒤 도착하는 결과 (Phase 1) |
| **리스너 유실 대응** | **서버 하트비트 대신 3단 방어 (Phase 2)** — 예방(절전 예외) + 자가진단(앱 진입 배너, 서버 무관) + 가시화(대시보드 "마지막 감지 N일 전"). 서버가 죽은 클라이언트를 쫓아가지 않고, 사용자가 앱을 열었을 때 즉시 알아채고 복구하게 만든다. 사유는 §10 |

### 3.3 SSE vs FCM 역할 분담

```
승인 직후 ~ 최대 3분 (사용자가 화면에서 대기)   → SSE     (실시간 진행 표시)
그 이후 발생하는 모든 결과·실패·만료             → FCM     (앱 상태 무관 도달)
결제 발생 시점의 매수 제안                        → 로컬 알림 (현행 유지)
```

⚠️ **중복 방지 규칙**: SSE 연결이 살아있는 이벤트는 FCM을 보내지 않는다. 백엔드가 `SseConnectionManager.isConnected(eventId)`로 판정해 **서버에서 억제**한다. FE는 중복 처리 로직을 넣을 필요가 없다.

---

## 4. 작업 단계

| Phase | 내용 | 상태 |
| --- | --- | --- |
| **Phase 0** | FCM 없이 즉시 고칠 수 있는 것 (허위 문구 제거 + 누락 SSE 보강) | 선행 |
| **Phase 1** | FCM 결과 알림 7종 — **핵심** | 본작업 |
| **Phase 2** | 리스너 유실 3단 방어 (예방·자가진단·가시화, 서버 신규 인프라 0) | 후속 |

---

## 5. 프론트엔드 작업 지시

### Phase 0 — 즉시 (FE 단독, 백엔드 대기 불필요)

- [ ] **`src/views/payments/PaymentReviewView.vue` L373 문구 수정**
  - 현재: `체결이 완료되면 스마트폰 푸시 알림으로 안내해 드립니다. 지금 앱을 자유롭게 이용하셔도 됩니다.`
  - Phase 1 완료 전까지 임시: `체결 결과는 결제 내역 화면에서 확인하실 수 있어요. 지금 앱을 자유롭게 이용하셔도 됩니다.`
  - Phase 1 완료 후 원래 문구로 복구
- [ ] **`MainActivity.java` 권한 요청 순서 분리**
  - 현재 `onCreate`에서 `promptNotificationAccessIfNeeded()`(설정 화면으로 이탈) 직후 `requestPostNotificationsIfNeeded()`(권한 다이얼로그)를 호출한다. 화면 전환과 다이얼로그가 겹쳐 **`POST_NOTIFICATIONS`가 조용히 거부될 수 있다.**
  - 거부되면 리스너는 결제를 정상 전송하지만 알림은 영원히 뜨지 않는다 (`postFeedback()`의 `catch (SecurityException)`이 로그만 남김).
  - **수정**: `POST_NOTIFICATIONS`를 먼저 요청하고, 결과 콜백(`onRequestPermissionsResult`) 이후에 알림 접근 설정 화면으로 유도할 것.

### Phase 1 — FCM 수신 (백엔드 API 완료 후 착수)

#### 1) Firebase 프로젝트 세팅
- [ ] Firebase 콘솔에서 프로젝트 생성 → Android 앱 등록 (패키지명 **`com.tikkle.app`**)
- [ ] `google-services.json` 다운로드 → `android/app/google-services.json` 배치
  - ✅ **`android/app/build.gradle` L86~93에 조건부 `google-services` 플러그인 적용이 이미 구현되어 있다.** 파일만 넣으면 자동 활성화되며 gradle 수정 불필요
- [ ] `google-services.json`을 **`.gitignore`에 추가** (프로젝트 시크릿 정책 준수)
- [ ] Firebase 콘솔의 **서비스 계정 비공개 키(JSON)를 백엔드 담당자에게 별도 채널로 전달** (Git 커밋 금지)

#### 2) 플러그인 및 채널
- [ ] `npm i @capacitor/push-notifications` → `npx cap sync android`
- [ ] 앱 부팅 시 **알림 채널 생성** (미생성 시 Android 8+ 에서 알림이 표시되지 않을 수 있음)
  ```ts
  await PushNotifications.createChannel({
    id: 'tikkle_payment_result',
    name: '투자 결과 알림',
    description: '매수 체결·실패·만료 결과를 알려줍니다.',
    importance: 4,          // IMPORTANCE_HIGH
    visibility: 1,
  })
  ```
  > 채널 ID는 백엔드가 페이로드에 넣는 값과 **정확히 일치**해야 한다 → `tikkle_payment_result`
- [ ] `POST_NOTIFICATIONS` 권한은 `MainActivity.java:26`에 **이미 구현되어 있으므로 추가 작업 불필요** (Phase 0의 순서 수정만 반영)

#### 3) 토큰 등록 / 해제
- [ ] `PushNotifications.register()` → `registration` 리스너에서 토큰 획득
- [ ] **로그인 성공 직후** 및 **`bootstrap()` 시** `POST /api/users/me/device-token` 호출 (§6.1)
- [ ] `registrationError` 리스너에서 실패 로깅
- [ ] **로그아웃 시** `DELETE /api/users/me/device-token` 호출 (§6.2) — 누락하면 로그아웃한 기기로 알림이 계속 간다
- [ ] 토큰은 재설치·앱 데이터 삭제·장기 미사용 시 갱신되므로 **매 앱 실행마다 재등록**한다 (서버는 멱등 upsert 처리)

#### 4) 수신 처리
- [ ] `pushNotificationActionPerformed` (알림 탭) → `data.deepLink` 값을 기존 `navigateFromDeepLink()`에 그대로 전달
- [ ] `pushNotificationReceived` (앱 포그라운드) → 알림을 띄우지 말고 **인앱 처리** (토스트/배지/목록 갱신). 포그라운드에서 시스템 알림까지 띄우면 중복으로 보인다
- [ ] **`main.ts`의 `navigateFromDeepLink()` 확장** — 현재 `payments/review`만 처리한다. 아래 3종을 추가로 라우팅할 것 (§8)
  - `tikkle://payments` → `/payments`
  - `tikkle://settings/api-key` → `/settings/api-key`
  - `tikkle://home` → `/`

### Phase 2 — 리스너 유실 대응 (후속, 3단 방어)

> ⚠️ **서버 하트비트 방식은 채택하지 않는다.** 리스너가 가장 확실히 죽는 케이스(강제 중지)에서는 이를 깨울 FCM도 함께 차단되어 감지해도 도달할 수단이 없고, 비행기 모드·데이터 절약·해외 체류·장기 미사용이 전부 오탐으로 잡혀 멀쩡한 사용자에게 불안을 주기 때문이다. 대신 **"죽은 걸 서버가 쫓아가는" 대신 "사용자가 앱을 열었을 때 즉시 알아채고 복구하게" 만드는** 3단 방어로 대체한다. 이 방식은 **서버 신규 인프라가 0**이다. (판단 근거: §10)

**① 예방 — 애초에 죽지 않게 (FE, 효과 가장 큼)**
- [ ] 온보딩에 **배터리 최적화 예외 요청** 추가 — `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 인텐트
- [ ] 삼성 기기: **설정 → 배터리 → 백그라운드 사용 제한 → "사용 안 함 앱 절전"에서 티끌 제외** 안내 문구

**② 자가진단 — 서버를 거치지 않는다 (FE 단독)**
- [ ] 앱 진입 시 `isNotificationListenerEnabled()` 체크 → 꺼져 있으면 **홈 상단 경고 배너 + 설정 바로가기** 노출
- [ ] `androidx.work:work-runtime` 의존성 추가 → WorkManager 주기 작업으로 리스너 활성 상태 점검
- [ ] 점검 시 꺼져 있으면 **로컬 알림**으로 안내 (FCM 아님 — 서버 무관)
- [ ] (선택) 리스너가 끊긴 것이 감지되면 `NotificationListenerService.requestRebind(componentName)` 호출로 재바인딩 시도

**③ 가시화 — 기존 데이터 재활용, 신규 API 없음 (FE + BE 경미)**
- [ ] 결제 내역 화면 상단에 **"마지막 결제 감지: N일 전"** 표시 → 사용자가 유실을 스스로 발견하게 함
- [ ] BE: `GET /api/payments/dashboard` 응답에 `lastPaymentDetectedAt` 필드 1개 추가 (`PAYMENT_EVENTS`의 최신 `created_at` 조회). **신규 테이블·엔드포인트 불필요**

**④ 운영 지표 — 내부용, 사용자에게 알리지 않음 (BE, 선택)**
- [ ] "투자 ON 상태이면서 최근 30일 `PAYMENT_EVENTS` 0건인 사용자 수" 집계 쿼리 → 리스너 유실이 실제로 심각한지 판단하는 근거. **오탐 비용 0** (푸시하지 않으므로). 더 강한 대응이 필요한지 이 지표를 먼저 보고 결정한다

> 🚫 **강제 중지(`FLAG_STOPPED`) 상태는 어떤 조치도 하지 않는다.** FCM·브로드캐스트·WorkManager·AlarmManager가 전부 차단되는 Android 보안 모델의 의도된 동작이며, 사용자가 "이 앱을 멈춰"라고 명시적으로 지시한 상태다. 우회는 불가능하고 시도해서도 안 된다. 사용자가 앱을 다시 열면 ②·③이 즉시 상태를 알려 복구된다.

---

## 6. API 계약 (백엔드 제공)

모든 응답은 프로젝트 표준 `ApiResponse<T>` 래퍼(`{code, message, data}`)를 따른다.

### 6.1 디바이스 토큰 등록

```
POST /api/users/me/device-token
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "fcmToken": "dGhpcyBpcyBhIGZ..."
}
```

**응답 200**
```json
{ "code": "SUCCESS", "message": "요청에 성공했습니다." }
```

- **멱등**하다. 같은 토큰을 여러 번 보내도 안전하다.
- 이미 다른 계정에 등록된 토큰이면 **현재 계정으로 소유권이 이전**된다 (기기 공유·재로그인 대응).
- 실패해도 앱 동작을 막지 말 것. 재시도는 다음 앱 실행 시 자연히 이루어진다.

### 6.2 디바이스 토큰 해제 (로그아웃)

```
DELETE /api/users/me/device-token
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "fcmToken": "dGhpcyBpcyBhIGZ..."
}
```

**응답 200** — 토큰이 없어도 200 (멱등)

> ⚠️ 회원 탈퇴(`DELETE /api/users/me`) 시에는 서버가 토큰을 함께 삭제하므로 FE의 별도 호출이 불필요하다.

### 6.3 대시보드 응답 필드 추가 (Phase 2 ③)

리스너 유실 가시화를 위해 **기존** `GET /api/payments/dashboard` 응답에 필드 1개만 추가한다. **신규 엔드포인트는 만들지 않는다.**

```
GET /api/payments/dashboard
```
**추가 필드**
```json
{
  "lastPaymentDetectedAt": "2026-07-20T14:32:11"   // PAYMENT_EVENTS 최신 created_at, 없으면 null
}
```
- FE는 이 값으로 "마지막 결제 감지: N일 전"을 렌더링해 사용자가 리스너 유실을 스스로 발견하게 한다.

> 🚫 **리스너 하트비트 API는 만들지 않는다.** (사유: §5 Phase 2 상단 주석 및 §10)

---

## 7. FCM 페이로드 계약

### 7.1 메시지 형태

`notification` 블록과 `data` 블록을 **함께** 보낸다.
- `notification` → 앱이 백그라운드/종료 상태일 때 시스템이 자동 표시
- `data` → 탭 시 딥링크 라우팅 및 포그라운드 인앱 처리용

```json
{
  "notification": {
    "title": "매수 체결 완료 🎉",
    "body": "비트코인 0.00004123개를 매수했어요."
  },
  "data": {
    "type": "TRADE_SUCCESS",
    "eventId": "1024",
    "deepLink": "tikkle://payments"
  },
  "android": {
    "priority": "high",
    "notification": {
      "channel_id": "tikkle_payment_result"
    }
  }
}
```

> `data`의 모든 값은 **문자열**이다 (FCM 규격). `eventId`도 `"1024"`처럼 문자열로 온다 — 숫자로 쓰려면 FE에서 변환할 것.

### 7.2 알림 종류 7종

| `type` | 발생 시점 | 제목 | 본문 (예시) | `deepLink` |
| --- | --- | --- | --- | --- |
| `TRADE_SUCCESS` | 지연 체결 완료 | 매수 체결 완료 🎉 | {코인명} {수량}개를 매수했어요. | `tikkle://payments` |
| `TRADE_TIMEOUT` | 매수 10분 미체결 → 주문 취소 | 매수가 취소됐어요 | 10분간 체결되지 않아 주문을 취소했어요. 입금된 원화는 업비트 계좌에 있습니다. | `tikkle://payments` |
| `DEPOSIT_FAILED` | 업비트 입금 거절/취소 | 투자가 취소됐어요 | 업비트 원화 입금이 거절되어 투자를 진행하지 못했어요. 출금된 금액은 없습니다. | `tikkle://payments` |
| `DEPOSIT_TIMEOUT` | 2차 인증 210초 미완료 → 승인 대기로 복구 | 2차 인증 시간이 지났어요 | 2차 인증 시간이 지나 투자가 승인 대기 상태로 돌아갔어요. 다시 승인해 주세요. | `tikkle://payments` |
| `TRADE_FAILED` | 매수 주문 접수 실패 | 매수에 실패했어요 | 매수 주문이 접수되지 못했어요. 입금된 원화는 업비트 계좌에 있습니다. | `tikkle://payments` |
| `UPBIT_INVALID_KEY` | 업비트 401/403 | 업비트 연동이 만료됐어요 | 투자를 계속하려면 업비트 API 키를 다시 연동해 주세요. | `tikkle://settings/api-key` |
| `ORDER_EXPIRED` | 24시간 미승인 만료 | 투자 기회가 만료됐어요 | {가맹점} 결제 잔돈 {금액}원 투자가 24시간 경과로 취소됐어요. | `tikkle://payments` |

> `DEPOSIT_TIMEOUT`은 초기 6종에 없었다. 2차 인증을 카카오·네이버 앱에서 완료하는 동안 티끌 앱을 떠나 있는 것이 정상 경로라 SSE가 끊겨 있는데, 이 구간의 타임아웃만 "사용자가 화면 앞에 있다"고 가정해 SSE `TIMEOUT`만 발송하고 있었다. 인증을 놓친 사용자가 아무 통보도 받지 못하는 문제가 확인되어 추가했다.
>
> ⚠️ 초기 검토안(7종)에 있던 리스너 유실 알림(`LISTENER_INACTIVE`)은 **폐기**했다. 대상 기기가 죽은 케이스에서 FCM도 도달하지 않고 오탐 비용이 크기 때문이다. Phase 2 ②·③(FE 자가진단 + 대시보드 가시화)이 이를 대체한다. (사유: §10)
>
> **본문 문구는 백엔드가 생성한다.** FE는 표시만 하면 되며, 문구 수정 요청은 백엔드에 전달할 것.
> `type`은 향후 늘어날 수 있으므로 FE는 **알 수 없는 `type`을 만나면 `deepLink`만 따라가고 크래시하지 않도록** 처리한다.

---

## 8. 딥링크 계약

기존 스킴(`tikkle://`)을 그대로 확장한다. `AndroidManifest.xml`의 intent-filter는 `android:host="payments"`만 선언되어 있으므로 **host 추가 선언이 필요하다.**

| 딥링크 | 라우팅 대상 | 상태 |
| --- | --- | --- |
| `tikkle://payments/review?eventId=..&merchant=..&...` | `/payments/review` | ✅ 구현 완료 (변경 없음) |
| `tikkle://payments` | `/payments` | 🆕 추가 |
| `tikkle://settings/api-key` | `/settings/api-key` | 🆕 추가 |

**`AndroidManifest.xml` 수정 필요** — 현재 `host="payments"`만 선언되어 있어 `settings` host 추가가 필요하다.
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="tikkle" android:host="payments" />
    <data android:scheme="tikkle" android:host="settings" />
</intent-filter>
```

---

## 9. 테스트 체크리스트

### 알림 도달 (Phase 1)
- [ ] 앱 포그라운드 → 인앱 처리, 시스템 알림 **미표시**
- [ ] 앱 백그라운드 → 시스템 알림 표시, 탭 시 딥링크 정상 이동
- [ ] **최근앱 스와이프 종료** → 알림 표시 및 탭 시 앱 콜드스타트 + 딥링크 이동
- [ ] 재부팅 후 앱 미실행 → 알림 표시
- [ ] 로그아웃 후 → 알림이 **오지 않음** (토큰 해제 확인)
- [ ] 회원 탈퇴 후 → 알림이 **오지 않음**

### 기존 기능 회귀 (반드시 확인)
- [ ] 결제 발생 시 **매수 제안 로컬 알림**이 종전대로 동작 (FCM 도입으로 영향 없어야 함)
- [ ] 스와이프 종료 상태에서 결제 → 파싱·전송·로컬 알림 정상
- [ ] 승인 후 SSE 진행 표시 정상 (2차 인증 대기 스피너)
- [ ] SSE 연결 중에는 동일 이벤트의 **FCM이 오지 않음** (서버 억제 확인)

### 리스너 생존 (참고 측정)
- [ ] `adb shell pgrep -f com.tikkle.app` — 스와이프 전후 **PID 동일** 확인
- [ ] `adb logcat -s TikklePaymentListener` — 스와이프 후 `onListenerConnected` 재출력 여부
- [ ] **설정 → 강제 중지** 후 결제 → 유실되는 것 확인 (Phase 2 ②·③가 이를 사용자에게 어떻게 노출하는지 검증)

### Phase 2 (리스너 유실 대응)
- [ ] 알림 접근 권한 OFF 상태로 앱 진입 → 홈 상단 경고 배너 + 설정 바로가기 노출
- [ ] 강제 중지 후 앱 재실행 → ②·③가 즉시 상태를 알려 복구되는지
- [ ] 결제 없이 3주 경과 시나리오 → "마지막 결제 감지: N일 전" 정상 표시 (`lastPaymentDetectedAt`)

---

## 10. 하지 않기로 한 것

| 항목 | 이유 |
| --- | --- |
| 결제 스크래핑을 FCM으로 대체 | **원리적으로 불가능.** 타사 앱 알림은 우리 FCM을 경유하지 않는다 |
| 매수 제안 알림을 FCM으로 전환 | 서버 응답을 이미 받은 시점이라 지연만 추가. FCM도 같은 권한 필요 → 도달률 개선 없음 |
| 포그라운드 서비스(상시 알림)로 리스너 유지 | 시스템이 이미 `BIND_FOREGROUND_SERVICE`급 우선순위를 부여. 스와이프 생존이 실기기에서 확인됨. 상시 알림 UX 비용만 발생 |
| SSE 제거 | 승인 후 3분 대기 구간은 SSE의 실시간성이 FCM보다 우수 |
| **서버 하트비트로 리스너 생존 감시** | ① 가장 확실히 죽는 강제 중지 상태에서 깨울 FCM도 함께 차단되어 **감지해도 도달 불가** ② 비행기 모드·데이터 절약·해외 체류·장기 미사용이 전부 오탐 → 멀쩡한 사용자에게 불안 유발(금융성 서비스에서 비싼 비용) ③ 뱅크샐러드·브로콜리 등 동종 국내 가계부 앱도 서버 하트비트 대신 앱 진입 자가진단 방식을 씀. → **Phase 2 3단 방어로 대체** |
| `LISTENER_INACTIVE` FCM | 위와 동일 사유. 대상 기기가 죽은 상태에선 도달하지 않음 |
| 강제 중지(`FLAG_STOPPED`) 능동 대응 | Android 보안 모델상 우회 불가 — FCM·브로드캐스트·WorkManager·AlarmManager 전부 차단. **사용자가 명시적으로 "멈춰"라고 지시한 상태**이므로 우회해서도 안 된다. 재실행 시 Phase 2 ②·③가 즉시 복구 안내 |

---

## 11. 담당 및 순서

| 순서 | 작업 | 담당 | 선행 조건 |
| --- | --- | --- | --- |
| 1 | Phase 0 — 문구 수정, 권한 순서 분리 | FE | 없음 |
| 1 | Phase 0 — 누락 SSE 이벤트 보강 | BE | 없음 |
| 2 | Firebase 프로젝트 생성 + 서비스 계정 키 전달 | FE | 팀 Google 계정 |
| 3 | 토큰 등록/해제 API + FCM 발송 구현 | BE | 2 |
| 4 | 플러그인 연동 + 토큰 등록 + 딥링크 확장 | FE | 3 |
| 5 | 통합 테스트 (§9) | FE + BE | 4 |
| 6 | Phase 2 — ① 절전 예외 안내 · ② 자가진단 배너 · ③ 대시보드 `lastPaymentDetectedAt` | FE 중심 (③만 BE 경미) | 5 |

**문의**: 백엔드 담당 김정윤 / 프론트 담당 이형준

---

## 12. 관련 문서

- `docs/Tikkle_payment_investment_flow.md` — 결제·투자 파이프라인 전체 및 SSE 이벤트 계약
- `docs/Tikkle_requirements.md` — `FEAT-SYS-004` / `FEAT-SYS-005` 엔진 명세
- `docs/Tikkle_ERD.md` — `DEVICE_TOKENS` 테이블 정의