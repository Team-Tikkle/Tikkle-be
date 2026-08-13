# FE 핸드오프 — 결제 진행 상태 복구 & 결과 통보

> **작성일** 2026-08-13 · **대상** Tikkle-app (별도 repo) · **관련 이슈** [#149](https://github.com/Team-Tikkle/Tikkle-be/issues/149)
>
> 통합 테스트에서 발견된 "승인 후 앱을 나갔다 오면 화면이 사라지고 다시 투자하기가 실패한다" 문제의 백엔드 수정 사항과, 이를 완성하기 위해 앱에서 해야 할 작업을 정리했습니다.
>
> 상세 계약은 `Tikkle_payment_investment_flow.md` §7.2 / §8.3 / §8.4 / §9 / §9.1을 참조하세요.

---

## 0. 문제의 뿌리 — "사용자는 승인 후 앱을 떠난다"

업비트 2차 인증은 **카카오톡·네이버·하나 앱으로 전환해야** 완료됩니다. 즉 승인 직후 앱을 떠나는 것은 예외 상황이 아니라 **반드시 일어나는 정상 경로**이고, 그때 SSE 연결은 항상 끊깁니다.

기존 설계는 "승인 후 최대 3분은 사용자가 화면에서 대기한다"를 전제로 만들어졌고, 그 전제 위에서 세 가지가 무너져 있었습니다.

| 증상 | 원인 |
| --- | --- |
| 앱에 다시 들어와도 진행 중인 화면이 안 보임 | 진행 중인 건을 조회할 API가 없었음 |
| "다시 투자하기"를 누르면 실패(`PAYMENT-006`) | 이미 승인된 건도 `PENDING`으로 내려가 앱이 승인 버튼을 계속 노출 |
| 앱을 나가 있는 동안 끝난 결과를 통보받지 못함 | 가장 흔한 성공 경로를 포함해 4개 종료 경로에 FCM 발송이 누락 |

백엔드는 모두 수정했습니다. **아래 §5의 앱 작업이 있어야 사용자에게 실제로 보입니다.**

---

## 1. `PaymentViewStatus`에 `IN_PROGRESS` 추가 ⚠️ 계약 변경

`GET /api/payments`(결제 피드) 응답의 `status` 필드에 값이 하나 늘었습니다.

```
PENDING | IN_PROGRESS | INVESTED | CANCELED
           ^^^^^^^^^^^ 신규
```

| 내부 상태 | 이전 `status` | **현재 `status`** | 의미 |
| --- | --- | --- | --- |
| `PENDING_PURCHASE` | `PENDING` | `PENDING` | 승인 대기 — **승인/거절 가능** |
| `PENDING_DEPOSIT` | `PENDING` | **`IN_PROGRESS`** | 2차 인증 대기 중 — 승인/거절 불가 |
| `PENDING_TRADE` | `PENDING` | **`IN_PROGRESS`** | 체결 대기 중 — 승인/거절 불가 |
| `INVESTED` | `INVESTED` | `INVESTED` | 완료 |
| `NOT_INVESTED` / `FAILED` | `CANCELED` | `CANCELED` | 종료 |

### 앱이 해야 할 것

**승인/거절 버튼은 `status === 'PENDING'`일 때만 노출하세요.** `IN_PROGRESS`에서 승인을 요청하면 `409 PAYMENT-006 "결제 대기 상태가 아니므로 처리할 수 없습니다"`가 떨어집니다.

`IN_PROGRESS`는 진행 상태만 표시합니다(예: "2차 인증 대기 중", "체결 대기 중").

### `expiredAt` 의미 확장

`expiredAt`은 **현재 단계의 마감 시각**입니다. `PENDING_TRADE`에도 값이 생겼습니다.

| `status` | `expiredAt` |
| --- | --- |
| `PENDING` (`PENDING_PURCHASE`) | 결제 감지 + 24시간 |
| `IN_PROGRESS` (`PENDING_DEPOSIT`) | 입금 요청 + 210초 |
| `IN_PROGRESS` (`PENDING_TRADE`) | 주문 접수 + 10분 |

---

## 2. 신규 API — `GET /api/payments/in-progress`

승인 이후 아직 끝나지 않은 건을 반환합니다. **앱 재진입 시 화면 복구의 출발점입니다.**

```http
GET /api/payments/in-progress
Authorization: Bearer {JWT}
```

```json
{
  "code": "SUCCESS",
  "message": "요청에 성공했습니다.",
  "data": [
    {
      "eventId": 35,
      "status": "PENDING_DEPOSIT",
      "merchant": "스타벅스",
      "amount": 4560,
      "spareChange": 5440,
      "targetCoinMarket": "KRW-BTC",
      "targetCoinName": "비트코인",
      "expiresAt": "2026-08-05T19:05:03",
      "createdAt": "2026-08-05T19:01:33"
    }
  ]
}
```

- `status`는 **내부 상태 그대로** 내려갑니다 (`PENDING_DEPOSIT` 또는 `PENDING_TRADE`). 피드의 `PaymentViewStatus`와 다른 값이니 주의하세요.
- 진행 중인 건이 없으면 `data`는 **빈 배열** `[]` 입니다.
- `expiresAt`을 그대로 카운트다운에 쓸 수 있습니다.
- **`PENDING_PURCHASE`(승인 대기)는 포함되지 않습니다.** 그건 진행 중인 건이 아니라 대기 목록이고 결제 피드가 담당합니다.

### 화면 매핑 가이드

| `status` | 화면 |
| --- | --- |
| `PENDING_DEPOSIT` | "2차 인증을 완료해 주세요" + 남은 시간 (PAY-05) |
| `PENDING_TRADE` | "주문 접수 완료, 체결 대기 중" — 사용자를 붙잡지 말 것 |

---

## 3. SSE 재구독 지원

### 3-1. 재구독이 정상 경로입니다

`GET /api/payments/{eventId}/stream`은 **몇 번이든 다시 구독할 수 있습니다.** 같은 `eventId`로 재구독하면 서버가 이전 커넥션을 정리하고 교체하므로, 끊길 때마다 다시 연결하면 됩니다.

### 3-2. 구독 직후 현재 상태 스냅샷을 1회 보냅니다 (신규)

연결되면 `CONNECTED` 다음에 **현재 상태에 해당하는 이벤트가 1회 더** 옵니다. 끊겨 있던 동안 확정된 결과가 그대로 전달됩니다.

| 구독 시점의 결제 상태 | 받는 이벤트 | 서버 연결 |
| --- | --- | --- |
| `PENDING_DEPOSIT` | `PROCESSING` | 유지 (이후 3초마다 계속 옴) |
| `PENDING_PURCHASE` | `TIMEOUT` | 즉시 종료 — 재승인 안내 |
| `PENDING_TRADE` | `PENDING_TRADE` | 즉시 종료 |
| `INVESTED` | `SUCCESS` | 즉시 종료 |
| `FAILED` | `FAILED` | 즉시 종료 |
| `NOT_INVESTED` | `CLOSED` (신규) | 즉시 종료 |

**이벤트 이름과 data 형태는 기존 발송과 완전히 동일합니다.** 스냅샷용 분기를 따로 만들 필요 없이 기존 핸들러를 그대로 쓰면 됩니다.

이미 끝난 건에 재구독해도 210초를 기다리지 않고 즉시 결과를 받고 연결이 닫힙니다.

### 3-3. `CLOSED` 이벤트 추가

```json
{ "status": "CLOSED", "message": "투자가 진행되지 않고 종료된 결제 건" }
```

이미 `NOT_INVESTED`(사용자 거절 또는 24시간 만료)로 끝난 건에 재구독했을 때 옵니다. 진행 화면을 닫고 결제 내역으로 보내면 됩니다.

### 3-4. ⚠️ data 형태 주의 (기존 계약, 문서가 틀려 있었음)

| 이벤트 | data 형태 |
| --- | --- |
| `CONNECTED`, `PROCESSING`, **`TIMEOUT`** | **평문 문자열** |
| 그 외 전부 | JSON 객체 |

`TIMEOUT`이 평문이라는 점이 이전 문서에 빠져 있었습니다. **`JSON.parse`를 무조건 걸면 `TIMEOUT`에서 터집니다.**

---

## 4. FCM 알림 — 누락 경로 보강 + 타입 1종 추가

### 4-1. `DEPOSIT_TIMEOUT` 추가 (6종 → 7종)

| `type` | 제목 | 본문 | `deepLink` |
| --- | --- | --- | --- |
| `DEPOSIT_TIMEOUT` | 2차 인증 시간이 지났어요 | 2차 인증 시간이 지나 투자가 승인 대기 상태로 돌아갔어요. 다시 승인해 주세요. | `tikkle://payments` |

결제 건은 `PENDING_PURCHASE`로 복구되어 **재승인이 가능한 상태**입니다. 알림을 탭하면 결제 내역에서 다시 승인할 수 있게 유도해 주세요.

### 4-2. 알림이 나가지 않던 경로 4개 보강

기존 타입만 사용하므로 **앱 수정은 필요 없습니다.** 참고용으로만 적습니다.

| 상황 | 이전 | 현재 |
| --- | --- | --- |
| 인증 완료 후 **즉시 체결 성공** (가장 흔한 성공) | 알림 없음 | `TRADE_SUCCESS` |
| 매수 처리 중 예기치 못한 오류 | 알림 없음 | `TRADE_FAILED` |
| 주문 종료됐으나 체결 수량 0 (원화가 업비트에 잔류) | 알림·SSE 모두 없음 | `TRADE_FAILED` + SSE |
| `targetCoin` 정보 누락 (원화가 업비트에 잔류) | 알림·SSE 모두 없음 | `TRADE_FAILED` + SSE |

이제 **승인 이후 결제가 끝나는 모든 경로에서 알림이 나갑니다.**

### 4-3. 중복 걱정은 하지 않아도 됩니다

SSE 연결이 살아있는 이벤트는 서버가 FCM을 억제합니다. 앱에서 중복 제거 로직을 넣을 필요가 없습니다.

---

## 5. 앱에서 해야 할 작업 체크리스트

- [ ] **앱 재진입(onResume) 시 `GET /api/payments/in-progress` 호출** → 진행 중인 건이 있으면 해당 화면 복구
- [ ] 복구한 `eventId`로 **`/stream` 재구독**
- [ ] SSE 연결이 끊기면(백그라운드 전환·네트워크 변경) 복귀 시 위 두 단계를 다시 수행
- [ ] **결제 피드에서 승인/거절 버튼은 `status === 'PENDING'`일 때만 노출**, `IN_PROGRESS`는 진행 표시만
- [ ] `CLOSED` 이벤트 핸들러 추가
- [ ] `DEPOSIT_TIMEOUT` 알림 타입 처리 추가
- [ ] `TIMEOUT` data를 `JSON.parse` 하지 않도록 확인
- [ ] **FCM 연동 완료** — `@capacitor/push-notifications` 설치, 권한 요청, 토큰 획득 후 `POST /api/users/me/device-token` 호출. **이게 안 되면 위 알림이 하나도 도달하지 않습니다.**
- [ ] 로그아웃 시 `DELETE /api/users/me/device-token` 호출

### 표준 복구 절차

```
앱 진입 / 포그라운드 복귀
        │
        ▼
GET /api/payments/in-progress
        │
        ├─ [] (빈 배열) ────────▶ 평소 화면 (결제 내역 등)
        │
        └─ [{eventId, status, expiresAt, ...}]
                │
                ├─ status === 'PENDING_DEPOSIT'
                │     → "2차 인증을 완료해 주세요" + expiresAt 카운트다운
                │
                ├─ status === 'PENDING_TRADE'
                │     → "체결 대기 중" 안내
                │
                ▼
        GET /api/payments/{eventId}/stream 재구독
                │
                ├─ CONNECTED        (평문, 무시)
                ├─ 상태 스냅샷 1회   ← 끊겨 있던 동안의 결과가 여기서 복구됨
                └─ 이후 실시간 이벤트
```

---

## 6. 알아두면 좋은 것

- **SSE가 끊기는 것은 오류가 아닙니다.** 2차 인증 중에는 반드시 끊깁니다. 에러 화면을 띄우지 말고 조용히 재구독하세요.
- **최종 이벤트를 받으면 반드시 스트림을 닫으세요**(`close()`/abort). 서버도 `complete()` 하지만, 브라우저/웹뷰의 자동 재연결을 막아야 합니다.
- **`TIMEOUT`은 "실패"가 아니라 "재시도 가능"입니다.** 결제 건이 `PENDING_PURCHASE`로 복구되어 다시 승인할 수 있습니다. (현재 `PaymentReviewView`가 `TIMEOUT`을 로컬에서 '취소'로 표시하는데, 목록을 새로고침하면 '대기 중'으로 돌아와 사용자가 혼란스럽습니다.)
- **승인 재요청은 막혀 있습니다.** `PENDING_DEPOSIT` 상태에서 승인을 다시 요청하면 409입니다. 기존 입금 요청이 살아있는데 재요청하면 2차 인증이 두 개 발송되어 원화가 이중 입금될 수 있어 의도적으로 막았습니다. 재승인은 210초 타임아웃 이후에만 가능합니다.

---

## 7. 관련 문서

- `Tikkle_payment_investment_flow.md` — 파이프라인 전체, 상태 모델, SSE 계약(§9), FCM 계약(§9.1)
- `Tikkle_notification_spec.md` — FCM 페이로드·딥링크 상세, 알림 7종
- Swagger — `https://api.tikkle.xyz/swagger-ui/index.html`
