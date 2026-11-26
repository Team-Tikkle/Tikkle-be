#!/usr/bin/env bash
#
# 운영 서버에서 실행되는 배포 스크립트. 배포 워크플로(.github/workflows/deploy.yml·rollback.yml)가 SSM 명령으로 부른다.
#
#   bash deploy-remote.sh <이미지 URI (…/tikkle-be:<커밋 SHA>)>
#
# 이 스크립트와 docker-compose.yml, deploy-env.py, application-prod.yml 은 배포할 이미지 안(/app/deploy)에 들어 있다.
# SSM 명령이 이미지를 받아 이 파일들을 꺼낸 뒤 이 스크립트를 실행한다. 그래서 이미지와 배포 파일이 늘 같은 커밋이다.
#
# 하는 일
#   1. SSM Parameter Store /tikkle/prod 아래 값을 모두 읽어 .env 를 만든다(권한 600). 운영 yml 의 ${이름} 이 모두 있는지 확인한다.
#   2. compose 파일과 이미지를 바꿔 다시 띄운다. Redis 는 설정이 같으면 그대로 둔다(refresh_token 이 Redis 에 있다).
#   3. 앱 컨테이너가 healthy 가 될 때까지 기다린다. 실패하면 마지막으로 성공한 배포(.env·compose)로 되돌리고 실패로 끝난다.
#      성공한 배포만 rollback/ 에 남긴다 — 실패한 배포나 첫 배포 실패를 "직전 배포"로 착각하지 않게 한다.
#   4. 이 저장소의 이미지는 지금 것과 직전 것만 남긴다.
#
# 출력은 요약만 한다. 이 출력은 GitHub Actions 로그(공개 레포)에 실린다 — 비밀값과 앱 로그를 찍지 않는다.
# 앱 로그는 서버에서 `sudo docker logs --tail 200 tikkle-be` 로 본다.
set -euo pipefail

IMAGE_URI="${1:?사용법: deploy-remote.sh <이미지 URI>}"
APP_DIR="${TIKKLE_APP_DIR:-/opt/tikkle}"
REGION="${TIKKLE_AWS_REGION:-ap-northeast-2}"
HEALTH_TIMEOUT="${TIKKLE_HEALTH_TIMEOUT:-300}"
PARAM_PATH="/tikkle/prod"
PROJECT="tikkle"
APP_CONTAINER="tikkle-be"

STAGE_DIR="$(cd "$(dirname "$0")" && pwd)"
ROLLBACK_DIR="$APP_DIR/rollback"
REPOSITORY="${IMAGE_URI%:*}"
TAG="${IMAGE_URI##*:}"

log() { echo "[deploy] $*"; }

now() { date -u +%Y-%m-%dT%H:%M:%SZ; }

compose() {
  docker compose -p "$PROJECT" --project-directory "$APP_DIR" -f "$APP_DIR/docker-compose.yml" "$@"
}

# compose 를 띄우고 결과를 요약해 찍는다. 실패하면 마지막 몇 줄만 보여 준다
compose_up() {
  local out
  if out="$(compose up -d --remove-orphans 2>&1)"; then
    return 0
  fi
  log "compose up 실패:"
  echo "$out" | tail -n 5 | sed 's/^/[compose] /'
  return 1
}

# .env 의 IMAGE_URI 값 (따옴표 없이)
image_in_env() {
  [[ -f "$1" ]] || return 0
  sed -n "s/^IMAGE_URI='\(.*\)'$/\1/p" "$1"
}

write_env() {
  local tmp="$APP_DIR/.env.new"
  (
    umask 077
    aws ssm get-parameters-by-path --region "$REGION" --path "$PARAM_PATH" --recursive --with-decryption --output json \
      | python3 "$STAGE_DIR/deploy-env.py" "$IMAGE_URI" "$STAGE_DIR/application-prod.yml" > "$tmp"
  )
  mv -f "$tmp" "$APP_DIR/.env"
}

# 앱 컨테이너가 기대한 이미지로 떠서 healthy 가 될 때까지 기다린다
wait_healthy() {
  local expected_image="$1" deadline=$((SECONDS + HEALTH_TIMEOUT))
  local info state="?" health="?" restarts=0 image=""
  while (( SECONDS < deadline )); do
    info="$(docker inspect -f '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}} {{.RestartCount}} {{.Config.Image}}' "$APP_CONTAINER" 2>/dev/null || echo "missing none 0 none")"
    read -r state health restarts image <<<"$info"
    if [[ "$image" == "$expected_image" && "$state" == "running" && "$health" == "healthy" ]]; then
      return 0
    fi
    if [[ "$image" == "$expected_image" && ( "$state" == "exited" || "$state" == "dead" || "$restarts" -gt 0 ) ]]; then
      log "앱 컨테이너가 멈췄다 — 상태 $state, 재시작 ${restarts}회"
      return 1
    fi
    sleep 5
  done
  log "${HEALTH_TIMEOUT}초 안에 healthy 가 되지 않았다 — 상태 $state, 헬스 $health"
  return 1
}

# 실패 원인을 짐작할 수 있는 요약: 헬스체크 마지막 결과 한 줄. 앱 로그는 찍지 않는다
report_failure() {
  local last
  last="$(docker inspect -f '{{json .State.Health}}' "$APP_CONTAINER" 2>/dev/null \
    | python3 -c 'import json,sys
h = json.load(sys.stdin) or {}
logs = h.get("Log") or []
print(logs[-1].get("Output", "").strip().replace("\n", " ")[:200] if logs else "")' 2>/dev/null || true)"
  if [[ -n "$last" ]]; then
    log "마지막 헬스체크: $last"
  fi
  log "앱 로그는 서버에서 확인: sudo docker logs --tail 200 $APP_CONTAINER"
}

prune_images() {
  local keep_previous="$1" ref
  docker images "$REPOSITORY" --format '{{.Repository}}:{{.Tag}}' | while read -r ref; do
    if [[ "$ref" != "$IMAGE_URI" && "$ref" != "$keep_previous" ]]; then
      if docker rmi "$ref" > /dev/null 2>&1; then
        log "이미지 정리: ${ref##*:}"
      fi
    fi
  done
  docker image prune -f > /dev/null 2>&1 || true
}

record() { echo "$(now) $1 $2" >> "$APP_DIR/deploy-history.log"; }

rollback() {
  local previous_image="$1"
  if [[ -z "$previous_image" ]]; then
    log "되돌릴 성공한 배포가 없다. 앱 컨테이너는 그대로 둔다"
    return 0
  fi
  log "직전 배포로 되돌린다: ${previous_image##*:}"
  cp -p "$ROLLBACK_DIR/.env" "$APP_DIR/.env"
  cp -p "$ROLLBACK_DIR/docker-compose.yml" "$APP_DIR/docker-compose.yml"
  if compose_up && wait_healthy "$previous_image"; then
    log "되돌리기 완료 — 직전 이미지 ${previous_image##*:} 가 healthy"
    record "${previous_image##*:}" "rolled-back"
  else
    log "되돌리기도 healthy 가 되지 않았다 — 서버에서 직접 확인이 필요하다"
    record "${previous_image##*:}" "rollback-failed"
  fi
}

main() {
  mkdir -p "$APP_DIR" "$ROLLBACK_DIR"
  cd "$APP_DIR"

  # 1. 되돌릴 곳 = 마지막으로 성공한 배포(성공할 때만 rollback/ 에 남긴다)
  local previous_image=""
  if [[ -f "$ROLLBACK_DIR/.env" && -f "$ROLLBACK_DIR/docker-compose.yml" ]]; then
    previous_image="$(image_in_env "$ROLLBACK_DIR/.env")"
  fi
  if [[ -n "$previous_image" ]]; then
    log "배포 시작: ${TAG:0:12} (직전 ${previous_image##*:})"
  else
    log "배포 시작: ${TAG:0:12} (성공한 직전 배포 없음)"
  fi

  # 2. 새 설정과 compose 로 다시 띄운다
  write_env
  cp -f "$STAGE_DIR/docker-compose.yml" "$APP_DIR/docker-compose.yml"

  # 3. 헬스
  if compose_up && wait_healthy "$IMAGE_URI"; then
    log "앱 healthy: ${TAG:0:12}"
    record "$TAG" "success"
    cp -p "$APP_DIR/.env" "$ROLLBACK_DIR/.env"
    cp -p "$APP_DIR/docker-compose.yml" "$ROLLBACK_DIR/docker-compose.yml"
    prune_images "$previous_image"
    log "완료"
    return 0
  fi

  report_failure
  record "$TAG" "failed"
  rollback "$previous_image"
  return 1
}

main
