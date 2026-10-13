#!/usr/bin/env bash
#
# EC2 위에서 실행되는 배포 스크립트. GitHub Actions 가 SSM send-command 로 호출한다.
#
#   ./infra/deploy-remote.sh <ECR_IMAGE_URI>
#
# 시크릿은 인자로 받지 않는다. SSM send-command 의 파라미터는 명령 이력에
# 평문으로 남기 때문이다. DB 자격증명은 이 스크립트가 Parameter Store 에서
# 직접 읽어 .env 를 만든다.
set -euo pipefail

IMAGE_URI="${1:?사용법: deploy-remote.sh <ECR_IMAGE_URI>}"
APP_DIR="/home/ec2-user/Tikkle-be"
REGION="ap-northeast-2"

cd "$APP_DIR"

echo "[deploy] Parameter Store 에서 시크릿 조회"
get_param() {
  aws ssm get-parameter --name "$1" --with-decryption \
    --region "$REGION" --query 'Parameter.Value' --output text
}

DB_URL="$(get_param /tikkle/prod/DB_URL)"
DB_USERNAME="$(get_param /tikkle/prod/DB_USERNAME)"
DB_PASSWORD="$(get_param /tikkle/prod/DB_PASSWORD)"

echo "[deploy] .env 생성"
umask 077
cat > .env <<EOF
SPRING_PROFILES_ACTIVE=prod
DB_URL=${DB_URL}
DB_USERNAME=${DB_USERNAME}
DB_PASSWORD=${DB_PASSWORD}
# application-prod.yml 이 \${REDIS_HOST} 를 참조하는 경우를 대비해 둔다.
# compose 의 SPRING_DATA_REDIS_HOST 와 같은 값이어야 한다.
REDIS_HOST=redis
IMAGE_URI=${IMAGE_URI}
EOF

echo "[deploy] ECR 로그인"
ECR_REGISTRY="$(echo "$IMAGE_URI" | cut -d/ -f1)"
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"

echo "[deploy] 이미지 pull: ${IMAGE_URI}"
docker pull "$IMAGE_URI"

# down 후 up 이 아니라 up -d 로 교체한다. redis 컨테이너를 불필요하게
# 재시작시키지 않기 위해서다 (refresh_token 이 redis 에 있다).
echo "[deploy] 컨테이너 교체"
docker compose up -d --remove-orphans

echo "[deploy] 미사용 이미지 정리"
docker image prune -f

echo "[deploy] 헬스 체크"
for i in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1 \
     || curl -fsS http://127.0.0.1:8080/swagger-ui/index.html >/dev/null 2>&1; then
    echo "[deploy] 앱 기동 확인 (${i}회차)"
    exit 0
  fi
  sleep 5
done

echo "[deploy] 헬스 체크 실패. 로그:"
docker compose logs --tail=120 app
exit 1
