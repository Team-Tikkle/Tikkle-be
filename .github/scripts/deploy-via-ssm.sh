#!/usr/bin/env bash
#
# 배포 워크플로(deploy.yml·rollback.yml)가 러너에서 실행한다.
# 운영 서버(태그 Role=api)에 SSM 명령으로 "이 이미지로 배포"를 보내고, 끝날 때까지 기다린 뒤 요약을 찍는다.
#
#   bash .github/scripts/deploy-via-ssm.sh <이미지 URI (…/tikkle-be:<커밋 SHA>)>
#
# 서버는 이미지를 받아 그 안의 배포 파일(/app/deploy)을 꺼내고 infra/deploy-remote.sh 를 실행한다.
# 비밀값은 명령에 넣지 않는다. 서버가 SSM Parameter Store 에서 직접 읽는다.
set -euo pipefail

IMAGE_URI="${1:?사용법: deploy-via-ssm.sh <이미지 URI>}"
REGION="${AWS_REGION:?AWS_REGION 이 필요하다}"
TAG="${IMAGE_URI##*:}"
STAGE_DIR="/opt/tikkle/deploy-files"

if [[ ! "$TAG" =~ ^[0-9a-f]{40}$ ]]; then
  echo "::error::이미지 태그는 커밋 SHA(40자)여야 한다: $TAG"
  exit 1
fi

# 서버에서 실행할 명령. 이미지를 받고, 배포 파일을 꺼내고, 그 커밋의 배포 스크립트를 실행한다
# 작은따옴표는 jq 프로그램(작은따옴표로 감쌈) 안에 직접 쓸 수 없어 --arg q 로 넘긴다
parameters="$(jq -n --arg image "$IMAGE_URI" --arg region "$REGION" --arg stage "$STAGE_DIR" --arg q "'" '{
  executionTimeout: ["900"],
  commands: [
    "set -eu",
    "IMAGE=\($q)\($image)\($q)",
    "REGISTRY=\"${IMAGE%%/*}\"",
    "STAGE=\($q)\($stage)\($q)",
    "aws ecr get-login-password --region \($region) | docker login --username AWS --password-stdin \"$REGISTRY\" > /dev/null 2>&1",
    "docker pull --quiet \"$IMAGE\" > /dev/null",
    "rm -rf \"$STAGE\" && mkdir -p \"$STAGE\"",
    "CID=\"$(docker create \"$IMAGE\")\"",
    "docker cp \"$CID:/app/deploy/.\" \"$STAGE/\"",
    "docker rm \"$CID\" > /dev/null",
    "bash \"$STAGE/deploy-remote.sh\" \"$IMAGE\""
  ]
}')"

COMMAND_ID="$(aws ssm send-command \
  --region "$REGION" \
  --document-name "AWS-RunShellScript" \
  --targets "Key=tag:Role,Values=api" \
  --max-concurrency 1 \
  --max-errors 0 \
  --timeout-seconds 120 \
  --comment "tikkle deploy ${TAG:0:12}" \
  --parameters "$parameters" \
  --query "Command.CommandId" --output text)"
echo "SSM 명령: $COMMAND_ID (이미지 ${TAG:0:12})"

# 끝날 때까지 기다린다 (최대 20분). 대상 서버가 정해지는 데 몇 초 걸린다
status="" instance_id="" waited=0
while (( waited < 1200 )); do
  invocations="$(aws ssm list-command-invocations --region "$REGION" --command-id "$COMMAND_ID" --output json)"
  count="$(jq '.CommandInvocations | length' <<<"$invocations")"
  if (( count == 0 )); then
    if (( waited >= 60 )); then
      echo "::error::태그 Role=api 인 서버를 찾지 못했다(SSM 에 연결된 서버가 없다)"
      exit 1
    fi
  elif (( count > 1 )); then
    echo "::error::태그 Role=api 인 서버가 ${count}대다. 서버 1대 구성에서만 이 배포를 쓴다"
    exit 1
  else
    status="$(jq -r '.CommandInvocations[0].Status' <<<"$invocations")"
    instance_id="$(jq -r '.CommandInvocations[0].InstanceId' <<<"$invocations")"
    case "$status" in
      Success|Failed|Cancelled|TimedOut|Undeliverable|Terminated|DeliveryTimedOut|ExecutionTimedOut) break ;;
    esac
  fi
  sleep 10
  waited=$((waited + 10))
done

echo "결과: ${status:-알 수 없음} (${waited}초)"
if [[ -n "$instance_id" ]]; then
  result="$(aws ssm get-command-invocation --region "$REGION" --command-id "$COMMAND_ID" --instance-id "$instance_id" --output json)"
  echo "::group::서버 출력"
  jq -r '.StandardOutputContent' <<<"$result"
  echo "::endgroup::"
  stderr_content="$(jq -r '.StandardErrorContent' <<<"$result")"
  if [[ -n "$stderr_content" ]]; then
    echo "::group::서버 오류 출력"
    echo "$stderr_content"
    echo "::endgroup::"
  fi
fi

if [[ "$status" != "Success" ]]; then
  echo "::error::배포 실패 (status=${status:-없음}). 서버 출력을 확인한다. 직전 배포가 있으면 서버가 그것으로 되돌렸다"
  exit 1
fi
