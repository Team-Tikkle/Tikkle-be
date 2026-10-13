# Tikkle AWS 인프라 구축 체크리스트

리전은 전부 **`ap-northeast-2` (서울)** 이다. 위에서 아래로 순서대로 진행한다.

구성:

```
Route 53 (tikkle.xyz)
└── api.tikkle.xyz → Elastic IP → EC2 t3.small
                                   ├─ Nginx (TLS 종단, SSE 프록시)
                                   ├─ tikkle-be 컨테이너 (127.0.0.1:8080)
                                   └─ redis 컨테이너 (AOF 영속화)
                                          │
                                   RDS MySQL 8.4 db.t4g.micro (Single-AZ, private)
```

---

## 1. Elastic IP 할당 ← 가장 먼저

`EC2 → Elastic IP addresses → Allocate Elastic IP address`

- 태그: `Name = tikkle-api`
- **이 IP 는 사용자의 업비트 API 키 허용 IP 로 등록된다. 절대 릴리스하지 말 것.**
- 확보한 IP 를 적어둘 것 → 앱의 안내 문구 3곳을 이 값으로 교체해야 한다.

## 2. RDS 생성

`RDS → Create database → Standard create`

| 항목 | 값 |
| --- | --- |
| 엔진 | MySQL **8.4 (LTS)** — ⚠️ **8.0 은 2026-07-31 표준 지원 종료.** 선택 시 유료 Extended Support(vCPU 시간당 과금)에 자동 등록되어 월 $100 이상이 추가된다 |
| 엔진 수명 주기 지원 | **Extended Support 비활성화** (미래에 자동 유료 등록되는 것을 막는다) |
| 템플릿 | Dev/Test |
| 클래스 | **db.t4g.micro** (Graviton. RDS 는 AWS 가 관리하므로 EC2 의 x86 선택과 무관하게 더 싼 쪽을 쓴다) |
| 배포 | **단일 AZ** (Multi-AZ 는 비용 2배) |
| 스토리지 | gp3 **20GB**, 자동 조정은 **켜되 상한 25GiB** (실제로 늘 때만 과금. 끄면 디스크가 차는 순간 DB 가 멈춘다) |
| 퍼블릭 액세스 | **아니요** |
| 백업 보존 | **1일** (출시 전. 실사용자 생기면 7일로) |
| Performance Insights / 향상된 모니터링 | **끄기** (과금) |
| 삭제 방지 | **켜기** |
| 초기 DB 이름 | `tikkle_db` |

### ⚠️ 파라미터 그룹을 반드시 새로 만들어 붙일 것

기본 파라미터 그룹이면 **한글 가맹점명이 깨지고 시각이 UTC 로 저장된다.**

`RDS → Parameter groups → Create` (family: **`mysql8.4`**, 이름 `tikkle-mysql84`)

```
time_zone = Asia/Seoul
```

> ⚠️ 엔진 유형은 **MySQL Community**, 패밀리는 **`mysql8.4`** 를 고를 것.
> `aurora-mysql8.4` 를 고르면 파라미터 이름이 달라지고(`default_time_zone`, `time_zone` 읽기 전용)
> RDS MySQL 인스턴스에 붙일 수도 없다. (2026-09-01 에 이 실수로 한 번 다시 만들었다.)

### MySQL 8.4 파라미터 메모

| 파라미터 | 값 | 비고 |
| --- | --- | --- |
| `time_zone` | `Asia/Seoul` | Dynamic. 재부팅 불필요 |
| `character_set_server` | 건드리지 않음 | 8.4 엔진 기본값이 `utf8mb4` |
| `collation_server` | 건드리지 않음 | 기본값 `utf8mb4_0900_ai_ci` |
>
> 설정에 실패해도 무방하다. 아래 `DB_URL` 의 `forceConnectionTimeZoneToSession=true`
> 가 접속마다 세션 타임존을 KST 로 강제하므로 앱 동작은 보장된다.

`Tikkle_ERD.md` 의 DDL 은 문자셋·콜레이션을 고정하지 않고 서버 기본값을 따르므로,
인스턴스 기동 후 아래로 한 번 검증할 것 (데이터를 넣기 전이 유일한 기회다).

```sql
SHOW VARIABLES LIKE 'character_set_server';   -- utf8mb4 기대
SHOW VARIABLES LIKE 'collation_server';       -- utf8mb4_0900_ai_ci 기대
SELECT @@global.time_zone, NOW();
```

생성 후 인스턴스에 연결하고 **재부팅**해야 적용된다.

## 3. EC2 생성

`EC2 → Launch instance`

| 항목 | 값 |
| --- | --- |
| AMI | Amazon Linux 2023 (x86_64) |
| 타입 | **t3.small** |
| 스토리지 | gp3 **20GB** |
| 키 페어 | **없이 진행** (SSM 으로 접속한다) |
| 퍼블릭 IP | 자동 할당 후 1번의 EIP 로 교체 |

### 보안 그룹

| 포트 | 소스 | 용도 |
| --- | --- | --- |
| 80 | `0.0.0.0/0` | HTTP (certbot 챌린지 + HTTPS 리다이렉트) |
| 443 | `0.0.0.0/0` | HTTPS |
| ~~22~~ | — | **열지 않는다.** 접속은 SSM Session Manager |
| ~~8080~~ | — | **열지 않는다.** Nginx 가 프록시 |

RDS 보안 그룹에는 **이 EC2 보안 그룹에서만** 3306 인바운드를 허용한다.

### IAM 인스턴스 프로파일

역할을 만들어 EC2 에 붙인다 (`EC2 → Actions → Security → Modify IAM role`).

- `AmazonSSMManagedInstanceCore` (관리형)
- `AmazonEC2ContainerRegistryReadOnly` (관리형)
- 인라인 정책 — Parameter Store 읽기:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["ssm:GetParameter", "ssm:GetParameters"],
    "Resource": "arn:aws:ssm:ap-northeast-2:<ACCOUNT_ID>:parameter/tikkle/prod/*"
  }, {
    "Effect": "Allow",
    "Action": "kms:Decrypt",
    "Resource": "*",
    "Condition": {"StringEquals": {"kms:ViaService": "ssm.ap-northeast-2.amazonaws.com"}}
  }]
}
```

## 4. SSM Parameter Store 에 DB 자격증명 등록

`Systems Manager → Parameter Store → Create parameter` (전부 **SecureString**)

| 이름 | 값 |
| --- | --- |
| `/tikkle/prod/DB_URL` | `jdbc:mysql://<RDS엔드포인트>:3306/tikkle_db?sslMode=REQUIRED&connectionTimeZone=Asia/Seoul&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8` |
| `/tikkle/prod/DB_USERNAME` | RDS 마스터 사용자 |
| `/tikkle/prod/DB_PASSWORD` | RDS 마스터 비밀번호 |

> 나머지 앱 시크릿(JWT, AES, HMAC, Gemini, DeepSeek, CoolSMS 등)은 기존 방식대로
> GitHub Secret `APPLICATION_PROD_YML` 로 주입되어 이미지에 포함된다.

## 5. ECR 리포지토리

`ECR → Create repository` → 이름 **`tikkle-be`** (Private)

## 6. GitHub OIDC + 배포 역할

### 6-1. OIDC 자격 증명 공급자

`IAM → Identity providers → Add provider → OpenID Connect`

- Provider URL: `https://token.actions.githubusercontent.com`
- Audience: `sts.amazonaws.com`

### 6-2. 배포용 역할 (`tikkle-github-deploy`)

신뢰 정책:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {
      "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com"
    },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": {
        "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
      },
      "StringLike": {
        "token.actions.githubusercontent.com:sub": "repo:Team-Tikkle/Tikkle-be:ref:refs/heads/main"
      }
    }
  }]
}
```

권한 정책:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": [
      "ecr:GetAuthorizationToken",
      "ecr:BatchCheckLayerAvailability",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
      "ecr:BatchGetImage"
    ],
    "Resource": "*"
  }, {
    "Effect": "Allow",
    "Action": ["ssm:SendCommand", "ssm:ListCommandInvocations", "ssm:GetCommandInvocation"],
    "Resource": "*"
  }]
}
```

## 7. GitHub Secrets 등록

`Settings → Secrets and variables → Actions`

| 이름 | 값 |
| --- | --- |
| `AWS_DEPLOY_ROLE_ARN` | 6-2 에서 만든 역할 ARN |
| `EC2_INSTANCE_ID` | `i-0123...` |
| `APPLICATION_PROD_YML` | 기존 값 유지 (**DB 접속 정보는 Parameter Store 로 이동**) |

기존 GCP 시크릿(`GCP_WIF_PROVIDER`, `GCP_SA_EMAIL`, `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`, `REDIS_HOST`)은 배포 성공을 확인한 뒤 삭제한다.

## 8. Route 53

`Route 53 → Hosted zones → Create hosted zone → tikkle.xyz`

가비아 DNS 관리툴의 기존 레코드를 **전부** 복제한다. 하나라도 빠지면 메일이 끊긴다.

| 호스트 | 타입 | 값 |
| --- | --- | --- |
| `api` | A | **1번의 Elastic IP** |
| `@` | MX | `10 mx.zoho.com` / `20 mx2.zoho.com` / `50 mx3.zoho.com` |
| `@` | TXT | `v=spf1 include:zoho.com ~all` |
| `@` | TXT | `zoho-verification=zb35484280.zmverify.zoho.com` |
| `zmail._domainkey` | TXT | 기존 DKIM 값 전문 |
| `_dmarc` | TXT | `v=DMARC1; p=none; rua=mailto:support@tikkle.xyz` (신규, 선택) |

> 레코드 복제를 **완료한 뒤에만** 가비아에서 네임서버를 Route 53 의 NS 4개로 교체한다.
> 순서가 바뀌면 메일과 도메인이 동시에 끊긴다.

랜딩 페이지(Amplify)용 `tikkle.xyz` / `www` 레코드는 Phase 4 에서 추가한다.

## 9. 서버 부트스트랩

`EC2 → 인스턴스 선택 → Connect → Session Manager` 로 접속:

```bash
sudo dnf install -y git
sudo git clone https://github.com/Team-Tikkle/Tikkle-be.git /home/ec2-user/Tikkle-be
cd /home/ec2-user/Tikkle-be && sudo bash infra/bootstrap.sh
```

`bootstrap.sh` 가 하는 일: 시스템 패키지 → Docker + Compose v2 → 스왑 2GB →
Nginx 설정 배치 → certbot 으로 TLS 발급.

> 8번(DNS)이 전파된 뒤에 실행해야 certbot HTTP-01 챌린지가 통과한다.
> 먼저 실행했다면 전파 후 아래를 수동으로:
> ```
> sudo certbot --nginx -d api.tikkle.xyz --agree-tos -m support@tikkle.xyz --redirect
> ```

## 10. 첫 배포

`main` 에 푸시하면 GitHub Actions 가 빌드 → ECR 푸시 → SSM 배포까지 수행한다.

### ⚠️ 배포 직후 반드시: 코인 메타데이터 채우기

`COIN_METADATA` 가 비어 있으면 `PAYMENT_EVENTS.target_coin_market` 의 FK 제약
때문에 AI 추천이 실패한다. `CoinSyncScheduler` 는 **04:00 KST 에만** 돈다.

첫 배포가 04:00 이전이면 스케줄러를 기다리지 말고 수동으로 동기화를 트리거할 것.

### 검증

- [ ] `https://api.tikkle.xyz/swagger-ui/index.html` 200
- [ ] TLS 인증서 유효 (자물쇠)
- [ ] `http://` → `https://` 리다이렉트
- [ ] 8080 포트가 외부에서 닫혀 있음
- [ ] SSE: `GET /api/payments/{id}/stream` 이 즉시 이벤트를 흘리는지 (버퍼링 확인)
- [ ] 한글 가맹점명이 DB 에 정상 저장 (파라미터 그룹 확인)
- [ ] `dig api.tikkle.xyz` → Elastic IP
- [ ] 메일 송·수신 정상 (`support@tikkle.xyz`)

---

## 비용 (추정)

| 항목 | 월 |
| --- | --- |
| EC2 t3.small | ~$19.0 |
| EBS gp3 30GB | ~$2.8 |
| Public IPv4 (EIP) | ~$3.6 |
| RDS db.t4g.micro | ~$17.0 |
| RDS 스토리지 20GB | ~$2.6 |
| Route 53 호스팅 영역 | $0.50 |
| **합계** | **~$45.5** |

신규 계정 크레딧 **$200 → 약 4.4개월**. (실제 값은 AWS Pricing Calculator 로 확인할 것)

**절대 만들지 말 것: NAT Gateway (월 $32+).** 퍼블릭 서브넷 + EIP 로 충분하다.
