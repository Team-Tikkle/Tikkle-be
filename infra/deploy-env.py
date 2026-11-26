#!/usr/bin/env python3
"""SSM Parameter Store 값으로 compose 의 .env 를 만든다. 운영 서버의 deploy-remote.sh 가 부른다.

    aws ssm get-parameters-by-path --path /tikkle/prod ... --output json \\
      | python3 deploy-env.py <IMAGE_URI> <application-prod.yml> > .env

- 환경 변수 이름은 파라미터 경로의 마지막 부분이다 (/tikkle/prod/DB_URL -> DB_URL).
- 값은 작은따옴표로 감싼다. compose 는 작은따옴표 안의 $ 를 변수로 풀지 않는다(DB_URL 의 & 와 비밀값의 $ 가 그대로 들어간다).
  값에 작은따옴표나 줄바꿈이 있으면 그대로 넣을 수 없어 멈춘다.
- 운영 yml 의 ${이름} 자리 표시자가 SSM 에 모두 있는지 확인한다. 없으면 앱이 기동에 실패하므로 배포 전에 멈춘다.
- 값은 어디에도 출력하지 않는다. 오류와 요약에는 이름만 쓴다.
"""
import json
import re
import sys

NAME_PATTERN = re.compile(r"[A-Z][A-Z0-9_]*")
PLACEHOLDER_PATTERN = re.compile(r"\$\{([A-Z0-9_]+)}")
RESERVED = ("SPRING_PROFILES_ACTIVE", "IMAGE_URI")


def err(message):
    # 서버 로캘과 상관없이 UTF-8 로 쓴다
    sys.stderr.buffer.write((message + "\n").encode("utf-8"))
    sys.stderr.flush()


def fail(message):
    err(f"[deploy] .env 를 만들 수 없다: {message}")
    sys.exit(1)


def quoted(name, value):
    if "'" in value or "\n" in value or "\r" in value:
        fail(f"값에 작은따옴표나 줄바꿈이 있다 — {name}")
    return f"{name}='{value}'"


def main():
    if len(sys.argv) != 3:
        fail("사용법: deploy-env.py <IMAGE_URI> <application-prod.yml>")
    image_uri, yml_path = sys.argv[1], sys.argv[2]

    parameters = json.load(sys.stdin).get("Parameters", [])
    values = {}
    for parameter in parameters:
        name = parameter["Name"].rsplit("/", 1)[-1]
        if not NAME_PATTERN.fullmatch(name):
            fail(f"환경 변수로 쓸 수 없는 이름 — {name}")
        if name in RESERVED:
            fail(f"배포 스크립트가 정하는 이름이라 SSM 에 두지 않는다 — {name}")
        values[name] = parameter["Value"]

    with open(yml_path, encoding="utf-8") as yml:
        required = sorted(set(PLACEHOLDER_PATTERN.findall(yml.read())))
    missing = [name for name in required if name not in values]
    if missing:
        fail("SSM 에 없는 값 — " + ", ".join(missing))

    lines = [quoted("SPRING_PROFILES_ACTIVE", "prod"), quoted("IMAGE_URI", image_uri)]
    lines += [quoted(name, values[name]) for name in sorted(values)]
    # 줄바꿈은 플랫폼과 상관없이 LF 로 쓴다
    sys.stdout.buffer.write(("\n".join(lines) + "\n").encode("utf-8"))
    err(f"[deploy] .env: SSM 값 {len(values)}개 (운영 yml 이 쓰는 {len(required)}개 모두 있음)")


if __name__ == "__main__":
    main()
