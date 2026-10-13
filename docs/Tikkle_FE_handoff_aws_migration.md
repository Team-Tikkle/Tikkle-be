# FE 핸드오프 — GCP → AWS 이전에 따른 앱·랜딩 페이지 작업

> **작성일** 2026-09-01 · **대상** Tikkle-app (별도 repo)
>
> 백엔드 인프라를 GCP에서 AWS로 옮기면서 앱에 반영해야 하는 값 변경과, AWS Activate 크레딧 신청을 위해 랜딩 페이지(`site/`)에 필요한 보완 사항을 정리했습니다.
>
> 인프라 구축 절차는 백엔드 레포의 `infra/README.md`를 참조하세요.

---

## 0. 배경

기존 GCP 환경(Compute Engine + Artifact Registry, `asia-northeast3`)이 종료되어 **서버가 내려가 있는 상태**입니다. AWS에 신규 구축 중이며, 그 과정에서 **서버 IP가 바뀝니다.**

| 구분 | 이전 (GCP) | 현재 (AWS) |
| --- | --- | --- |
| 리전 | `asia-northeast3` (서울) | `ap-northeast-2` (서울) |
| 서버 | Compute Engine `tikkle-server` | EC2 t3.small |
| **서버 IP** | `34.22.75.37` | **`15.164.79.198`** (Elastic IP, 고정) |
| DB | (GCP) | RDS MySQL 8.0 |
| 도메인 | `api.tikkle.xyz` | **변경 없음** |

**API 도메인(`api.tikkle.xyz`)은 그대로**이므로 `VITE_API_BASE_URL`은 바꿀 필요가 없습니다. 바뀌는 것은 **업비트에 등록하는 서버 IP**뿐입니다.

---

## 1. 🔴 업비트 허용 IP 변경 — 최우선

업비트 Open API 키는 **발급 시 등록한 IP에서만** 호출이 허용됩니다. 앱이 사용자에게 안내하는 IP가 실제 서버 IP와 다르면, 사용자가 키를 연동해도 **입금·매수가 전부 실패**합니다.

### 수정 위치

**`src/components/common/UpbitKeyGuide.vue:58`**

```diff
- IP 주소 등록란에 <span class="...">34.22.75.37</span>를 입력해 주세요.
+ IP 주소 등록란에 <span class="...">15.164.79.198</span>를 입력해 주세요.
```

이 컴포넌트가 온보딩(`OnboardingUpbitConnect`)과 설정(`LinkedAccountView`) 양쪽에서 재사용되므로 **한 곳만 고치면 됩니다.**

> 💡 **개선 제안 (별도 이슈 권장)**
> 서버 IP를 앱에 하드코딩하면 인프라가 바뀔 때마다 앱을 재배포해야 하고, 이미 설치된 사용자는 옛 IP를 계속 봅니다.
> 서버가 내려주는 값(예: 설정 조회 응답의 필드)으로 바꾸면 이 문제가 사라집니다. 이번 이전 후 정리하는 걸 권합니다.

### 기존 사용자 영향

현재 **실사용자가 없어(팀·테스트 계정뿐)** 공지나 유예 기간은 필요하지 않습니다. 이후 실사용자가 생긴 뒤에 IP가 또 바뀐다면 그때는 반드시 공지 → 유예 절차가 필요합니다.

---

## 2. 🔴 Android 빌드의 API 기본값 오타

**`android/app/build.gradle:30`**

```groovy
def resolveApiBase = localProps.getProperty('API_BASE_URL') ?:
        envProps.getProperty('VITE_API_BASE_URL') ?:
        project.findProperty('API_BASE_URL') ?: 'https://api.tikkle.com'
                                                            ^^^^ .com
```

최종 폴백이 **`api.tikkle.com`** 인데, 실제 도메인은 **`tikkle.xyz`** 입니다. `.env`와 `local.properties`가 모두 없는 환경에서 빌드하면 **우리 소유가 아닌 도메인**을 바라보는 APK가 나옵니다.

```diff
-        project.findProperty('API_BASE_URL') ?: 'https://api.tikkle.com'
+        project.findProperty('API_BASE_URL') ?: 'https://api.tikkle.xyz'
```

이번 이전과 직접 관련은 없지만, APK를 직배포할 예정이라 **잘못된 APK가 배포되는 사고를 막기 위해** 같이 고치는 걸 권합니다.

---

## 3. 배포 타이밍

앱 배포는 **백엔드가 살아난 뒤에** 해야 합니다.

```
[BE] AWS 인프라 구축 → api.tikkle.xyz 부활 (HTTPS 확인)
        ↓
[FE] §1 IP 수정 + §2 오타 수정 → APK 빌드 → 배포
        ↓
[FE] §5 랜딩 페이지 보완 → CTA에 APK 링크 연결
```

백엔드 완료 시점은 별도로 공유하겠습니다. 그 전에 §1·§2 수정과 §5 랜딩 작업은 **미리 진행 가능**합니다.

---

## 4. APK 배포 — GitHub Releases 권장

Play Store 출시 없이 APK를 직배포하기로 했습니다.

- **GitHub Releases**에 APK를 올리고 랜딩 CTA에서 그 링크로 연결
- 링크가 영구적이고 무료이며, 버전별 이력이 남습니다
- 랜딩에 **설치 안내 문구**를 반드시 함께 표기해 주세요 — Android는 스토어 외부 APK 설치 시 "알 수 없는 출처 허용"이 필요하고, Play Protect 경고가 뜹니다. 안내가 없으면 사용자가 설치를 포기합니다

---

## 5. 랜딩 페이지(`site/`) 보완

`site/index.html`은 완성도가 높아 **새로 만들 필요 없이 보완만** 하면 됩니다. 아래는 **AWS Activate 크레딧 심사** 기준으로 본 항목입니다. AWS는 자격 요건으로 "실제 콘텐츠가 있는 회사 웹사이트"를 요구하고, 국내 승인 후기에서 1차 거절 사유는 대부분 **미비·불일치**였습니다.

### 잘 되어 있는 것 (유지)

- 카피 품질, 문제 정의 → 솔루션 구성
- **"어떤 정보를, 왜 보나요"** 섹션 — 특히 "읽지 않는 것"(비밀번호·공동인증서 미요구). 핀테크 신뢰도에 가장 크게 기여합니다
- 원금 손실 고지 (`.notice`)
- 약관·방침을 `docs/*.md` 단일 출처에서 생성하는 구조(`scripts/build-legal.mjs`)
- 스크린샷이 데모 데이터라 개인정보 노출 없음 (확인 완료)

### 🔴 P0 — 신청 전 필수

| # | 항목 | 위치 | 조치 |
| --- | --- | --- | --- |
| 1 | **죽은 다운로드 버튼** | `index.html` `#download` 섹션의 `<a class="btn" href="#">Android 앱 다운로드</a>` (및 히어로의 `앱 다운로드`) | 심사관이 가장 먼저 누르는 버튼입니다. GitHub Releases APK 링크로 연결하거나, 배포 전이면 **"베타 신청"** + 구글폼으로 교체 |
| 2 | **팀 소개 섹션 없음** | 푸터 위 신설 | Activate 요건에 명시된 항목. 이름 · 역할 · LinkedIn 링크. 폼의 "직원 수"와 일치해야 함 |
| 3 | **전체가 한국어** | 전역 | 심사관이 한국어를 못 읽습니다. EN 토글 또는 `/en` 정적 페이지 |
| 4 | **OG 이미지 주석 처리** | `index.html` `<head>`의 `og:url` / `og:image` 주석 | 1200×630 이미지를 `site/og-image.png`로 두고 주석 해제. 링크 공유 시 빈 카드가 뜹니다 |

### 🟡 P1 — 심사 가산점

| # | 항목 | 조치 |
| --- | --- | --- |
| 5 | **기술/아키텍처 섹션** | 백엔드 레포의 `docs/architecture, flow/architecture.png` + 영문 한 줄:<br>`Spring Boot 4 · Java 21 · MySQL 8 · Redis 7 · Vue 3 + Capacitor · Upbit Open API · AWS (EC2 · RDS · Route 53 · Amplify)`<br>→ "실제 돌아가는 제품"과 "AWS를 실제로 쓴다"를 동시에 증명. 거절 후 이의 제기 때 이 섹션이 근거가 됩니다 |
| 6 | favicon 링크 없음 | `public/`에 파비콘이 있는데 `site/index.html`이 참조하지 않아 탭 아이콘이 빕니다 |
| 7 | `robots.txt` / `sitemap.xml` | 각 3줄. "관리되는 사이트" 신호 |

### 🟢 P2 — 성능

| # | 항목 | 조치 |
| --- | --- | --- |
| 8 | `screenshots/insights.png` **1.4MB** | WebP 변환 (100KB 이하 목표) |
| 9 | `fonts/PretendardVariable.woff2` **2MB** | 한글 서브셋으로 약 300KB (`site.css` 주석에 이미 TODO로 적혀 있음) |

### ⚠️ 내용 정확성 — "정보 불일치"로 걸릴 수 있는 지점

심사 탈락의 실제 1순위는 허위가 아니라 **사이트와 제품의 불일치**입니다.

| 현재 문구 | 실제 구현 | 제안 |
| --- | --- | --- |
| "사용하는 카드를 연결" | **케이뱅크 카드 한정** + 안드로이드 결제 푸시 스크래핑 | 지원 범위를 명시 |
| "연동 거래소에서 매수" | 업비트 | **"업비트"**를 명시하면 신뢰도가 올라갑니다 |
| 3단계 (`카드 연결 → 잔돈 → 승인`) | 실제 온보딩은 8단계. **업비트 API 키 연동 + 2차 인증 설정**이 빠져 있음 | 설치 후 당황하지 않도록 한 줄 추가 |
| "제외할 결제를 골라낼 수 있습니다" | 실제 기능은 **카테고리 변경**(`PATCH /api/payments/{id}/category`)과 **건별 승인/거절** | 실제 동작에 맞게 조정 |

### 배포

`site/`를 그대로 **AWS Amplify**에 올릴 예정입니다. `terms.html` / `privacy.html`이 이미 커밋되어 있어 빌드 없이 배포 가능합니다.

```yaml
version: 1
frontend:
  phases:
    build:
      commands: []
  artifacts:
    baseDirectory: site
    files:
      - '**/*'
```

약관 수정 시 로컬에서 `npm run build:site` 후 커밋하는 현재 방식은 그대로 유지하면 됩니다.

---

## 6. 앱 수정 후 백엔드에서 함께 갱신할 문서

`docs/화면설명서/화면설명서.md`의 두 곳에 **옛 IP `34.64.106.45`** 가 남아 있습니다 (현재 앱의 `34.22.75.37`과도 이미 불일치).

- `§ONB-02` 업비트 계정 연결 — API 키 발급 가이드 설명
- `§외부 연동 요약표` — 업비트 Open API 행

**§1의 앱 수정이 반영된 뒤** 백엔드 레포에서 `15.164.79.198`로 함께 갱신하겠습니다. 문서가 앱보다 앞서 나가지 않도록 순서를 맞춥니다.

---

## 요약 체크리스트

- [ ] `UpbitKeyGuide.vue:58` — 업비트 안내 IP를 `15.164.79.198`로 (§1)
- [ ] `android/app/build.gradle:30` — 기본값 `api.tikkle.com` → `api.tikkle.xyz` (§2)
- [ ] 백엔드 부활 확인 후 APK 빌드 → GitHub Releases 배포 (§3·§4)
- [ ] 랜딩 P0 4건: 다운로드 버튼 · 팀 섹션 · 영문 · OG 이미지 (§5)
- [ ] 랜딩 P1 3건: 아키텍처 섹션 · favicon · robots/sitemap (§5)
- [ ] 랜딩 문구 정확성 4건 (§5)
