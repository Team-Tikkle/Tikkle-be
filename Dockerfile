# 앱 이미지. jar 는 배포 워크플로가 먼저 빌드한다(./gradlew build -x test).
# 실행에는 JDK가 필요 없어 JRE만 쓴다(다중 아키텍처 이미지 — 운영 서버는 arm64).
FROM eclipse-temurin:21-jre-jammy

ENV TZ=Asia/Seoul

# root로 실행하지 않는다
RUN groupadd --system --gid 10001 tikkle \
 && useradd --system --uid 10001 --gid tikkle --no-create-home --shell /usr/sbin/nologin tikkle

WORKDIR /app
COPY --chown=tikkle:tikkle build/libs/*.jar app.jar

USER tikkle

# 8080: API (nginx 가 프록시), 8081: 헬스체크 전용 관리 포트(서버 안에서만 연다)
EXPOSE 8080 8081

# 기동에 1~2분 걸릴 수 있어(기동 시 코인 동기화 등) 시작 유예를 넉넉히 둔다
HEALTHCHECK --interval=15s --timeout=5s --start-period=180s --retries=3 \
  CMD curl -fsS http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
