#!/usr/bin/env bash
#
# EC2 최초 1회 셋업 (Amazon Linux 2023 / x86_64 / t3.small)
#
# 사용법 — SSM Session Manager 로 접속한 뒤:
#   sudo dnf install -y git
#   git clone https://github.com/Team-Tikkle/Tikkle-be.git /home/ec2-user/Tikkle-be
#   cd /home/ec2-user/Tikkle-be && sudo bash infra/bootstrap.sh
#
# 이 스크립트는 멱등하다. 다시 돌려도 안전하다.
set -euo pipefail

APP_DIR="/home/ec2-user/Tikkle-be"
APP_USER="ec2-user"
DOMAIN="api.tikkle.xyz"

log() { echo -e "\n\033[1;34m[bootstrap]\033[0m $*"; }

# ── 1. 시스템 패키지 ────────────────────────────────────────────
log "시스템 패키지 업데이트"
dnf update -y

log "docker / nginx / git 설치"
dnf install -y docker nginx git

# ── 2. Docker + Compose v2 ─────────────────────────────────────
log "Docker 기동 및 부팅 시 자동 시작 등록"
systemctl enable --now docker
usermod -aG docker "$APP_USER"

# AL2023 에는 compose 플러그인이 기본 포함되지 않는다. 수동 설치.
COMPOSE_VERSION="v2.31.0"
COMPOSE_PLUGIN_DIR="/usr/local/lib/docker/cli-plugins"
if ! docker compose version >/dev/null 2>&1; then
  log "docker compose 플러그인 설치 ($COMPOSE_VERSION)"
  mkdir -p "$COMPOSE_PLUGIN_DIR"
  curl -fsSL \
    "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
    -o "${COMPOSE_PLUGIN_DIR}/docker-compose"
  chmod +x "${COMPOSE_PLUGIN_DIR}/docker-compose"
fi
docker compose version

# ── 3. 스왑 (t3.small = 2GB. JVM 이 피크에서 OOM 나는 걸 막는다) ──
if [ ! -f /swapfile ]; then
  log "스왑 2GB 생성"
  dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# ── 4. 앱 디렉터리 ──────────────────────────────────────────────
if [ ! -d "$APP_DIR" ]; then
  log "레포가 없다. 먼저 git clone 을 하고 다시 실행할 것."
  exit 1
fi
chown -R "$APP_USER":"$APP_USER" "$APP_DIR"
# GitHub Actions 는 SSM 으로 root 가 git fetch 를 실행한다. 레포 소유자(ec2-user)와
# 달라 Git 2.35+ 가 "dubious ownership" 으로 거부하므로 안전 디렉터리로 등록해 둔다.
git config --system --add safe.directory "$APP_DIR"

# ── 5. Nginx ───────────────────────────────────────────────────
log "Nginx 설정 배치"
cp "${APP_DIR}/infra/nginx/tikkle.conf" /etc/nginx/conf.d/tikkle.conf

# AL2023 기본 conf 의 default server 와 충돌하지 않도록 확인만 한다.
nginx -t
systemctl enable --now nginx
systemctl reload nginx

# ── 6. TLS (Let's Encrypt) ─────────────────────────────────────
# ⚠️ 실행 전 조건: Route 53 에서 api.tikkle.xyz A 레코드가 이 서버의
#    Elastic IP 를 가리키고 있어야 한다 (HTTP-01 챌린지).
#    아직이면 이 단계를 건너뛰고, DNS 전파 후 아래를 수동 실행할 것:
#      sudo certbot --nginx -d api.tikkle.xyz --agree-tos -m support@tikkle.xyz --redirect
log "certbot 설치"
dnf install -y certbot python3-certbot-nginx || {
  echo "certbot dnf 설치 실패. pip 경로로 대체:"
  echo "  sudo dnf install -y python3-pip && sudo pip3 install certbot certbot-nginx"
}

if host "$DOMAIN" >/dev/null 2>&1; then
  log "TLS 인증서 발급 시도 ($DOMAIN)"
  certbot --nginx -d "$DOMAIN" \
    --non-interactive --agree-tos -m support@tikkle.xyz --redirect \
    || log "인증서 발급 실패. DNS 전파 후 수동으로 다시 실행할 것."
else
  log "$DOMAIN 이 아직 해석되지 않는다. DNS 설정 후 certbot 을 수동 실행할 것."
fi

# 갱신 타이머 (certbot 패키지가 등록하지만 명시적으로 확인)
systemctl enable --now certbot-renew.timer 2>/dev/null || true

# ── 7. 완료 ────────────────────────────────────────────────────
log "완료. 다음 단계:"
cat <<'EOF'

  1) SSM Parameter Store 에 아래 값이 등록되어 있는지 확인
       /tikkle/prod/DB_URL        (SecureString)
       /tikkle/prod/DB_USERNAME   (SecureString)
       /tikkle/prod/DB_PASSWORD   (SecureString)

  2) 첫 배포는 GitHub Actions(main 푸시)가 수행한다.
     수동으로 돌리려면:
       cd /home/ec2-user/Tikkle-be
       ./infra/deploy-remote.sh <ECR_IMAGE_URI>

  3) 배포 후 반드시 코인 메타데이터를 채울 것.
     COIN_METADATA 가 비어 있으면 AI 추천이 FK 제약으로 실패한다.
     (CoinSyncScheduler 는 04:00 KST 에만 돈다)

EOF
