# 앱 이미지. jar 는 배포 워크플로가 먼저 빌드한다(./gradlew build -x test).
# 실행에는 JDK가 필요 없어 JRE만 쓴다(다중 아키텍처 이미지 — 운영 서버는 arm64).
FROM eclipse-temurin:21-jre-jammy

ENV TZ=Asia/Seoul

# root로 실행하지 않는다
RUN groupadd --system --gid 10001 tikkle \
 && useradd --system --uid 10001 --gid tikkle --no-create-home --shell /usr/sbin/nologin tikkle

WORKDIR /app
COPY --chown=tikkle:tikkle build/libs/*.jar app.jar

# 배포 파일. 서버의 배포 명령이 이 이미지에서 꺼내 쓴다 — 이미지와 compose·배포 스크립트가 늘 같은 커밋이다.
# 앱은 이 파일들을 쓰지 않는다(운영 yml 은 jar 안의 것을 쓴다. 여기 사본은 배포 스크립트가 필요한 값 이름을 확인하는 데만 쓴다)
COPY docker-compose.yml infra/deploy-remote.sh infra/deploy-env.py src/main/resources/application-prod.yml /app/deploy/

USER tikkle

# 8080: API (nginx 가 프록시), 8081: 헬스체크 전용 관리 포트(서버 안에서만 연다)
EXPOSE 8080 8081

# 기동에 1~2분 걸릴 수 있어(기동 시 코인 동기화 등) 시작 유예를 넉넉히 둔다
HEALTHCHECK --interval=15s --timeout=5s --start-period=180s --retries=3 \
  CMD curl -fsS http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
