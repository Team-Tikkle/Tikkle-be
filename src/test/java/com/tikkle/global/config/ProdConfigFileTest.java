package com.tikkle.global.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 레포에 들어가는 운영 설정 파일(application-prod.yml)을 확인합니다.
 * 이 파일은 공개 레포에 있으므로 비밀값은 SSM 파라미터 이름의 자리 표시자로만 둡니다.
 * 실패 메시지에는 값을 넣지 않고 키 이름만 넣습니다.
 */
class ProdConfigFileTest {

    // ${이름} — 기본값(:...) 없이. 비밀값에 기본값이 있으면 그 기본값이 레포에 남는다.
    private static final Pattern PLACEHOLDER_WITHOUT_DEFAULT = Pattern.compile("\\$\\{[A-Z0-9_]+}");
    // "token"은 넣지 않는다 — 이 설정에서 token이 든 키는 만료 시간·최대 토큰 수 같은 비밀이 아닌 값이다
    private static final Pattern SECRET_LIKE_KEY = Pattern.compile(
            "(secret|password|api-key|\\.key$|credential|sender-number)", Pattern.CASE_INSENSITIVE);

    private static final List<String> SECRET_KEYS = List.of(
            "spring.datasource.url",
            "spring.datasource.username",
            "spring.datasource.password",
            "spring.ai.google.genai.api-key",
            "spring.ai.openai.api-key",
            "jwt.secret",
            "tikkle.payment.secret-key",
            "tikkle.encrypt.secret-key",
            "tikkle.fcm.credentials-base64",
            "coolsms.api.key",
            "coolsms.api.secret",
            "coolsms.sender-number"
    );

    private static Map<String, String> prod;

    @BeforeAll
    static void loadProdYaml() throws Exception {
        ClassPathResource resource = new ClassPathResource("application-prod.yml");
        assertThat(resource.exists()).as("application-prod.yml 이 레포(클래스패스)에 있어야 한다").isTrue();

        prod = new LinkedHashMap<>();
        for (PropertySource<?> source : new YamlPropertySourceLoader().load("prod", resource)) {
            EnumerablePropertySource<?> enumerable = (EnumerablePropertySource<?>) source;
            for (String name : enumerable.getPropertyNames()) {
                prod.put(name, String.valueOf(enumerable.getProperty(name)));
            }
        }
    }

    @Test
    @DisplayName("비밀값은 모두 기본값 없는 ${…} 자리 표시자다")
    void secretsArePlaceholdersOnly() {
        List<String> missing = new ArrayList<>();
        List<String> notPlaceholder = new ArrayList<>();
        for (String key : SECRET_KEYS) {
            String value = prod.get(key);
            if (value == null) {
                missing.add(key);
            } else if (!PLACEHOLDER_WITHOUT_DEFAULT.matcher(value.trim()).matches()) {
                notPlaceholder.add(key);
            }
        }

        assertThat(missing).as("운영 yml에 빠진 비밀값 키").isEmpty();
        assertThat(notPlaceholder).as("자리 표시자가 아닌 비밀값 키 (값은 출력하지 않는다)").isEmpty();
    }

    @Test
    @DisplayName("비밀처럼 보이는 키에 평문 값이 없다")
    void noLiteralValueUnderSecretLikeKeys() {
        List<String> suspicious = prod.entrySet().stream()
                .filter(e -> SECRET_LIKE_KEY.matcher(e.getKey()).find())
                .filter(e -> !PLACEHOLDER_WITHOUT_DEFAULT.matcher(e.getValue().trim()).matches())
                .map(Map.Entry::getKey)
                .toList();

        assertThat(suspicious).as("평문 값이 든 비밀처럼 보이는 키 (값은 출력하지 않는다)").isEmpty();
    }

    @Test
    @DisplayName("운영에서는 Swagger와 API 문서를 끈다")
    void disablesSwaggerInProd() {
        assertThat(prod.get("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(prod.get("springdoc.swagger-ui.enabled")).isEqualTo("false");
    }

    @Test
    @DisplayName("헬스 엔드포인트는 관리 포트 8081에 둔다")
    void usesManagementPort() {
        assertThat(prod.get("management.server.port")).isEqualTo("8081");
    }

    @Test
    @DisplayName("스키마는 Hibernate가 만들지 않고 대조만 한다")
    void validatesSchemaOnly() {
        assertThat(prod.get("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    @DisplayName("운영 CORS는 앱(Capacitor)과 FE 로컬 개발 서버의 출처만 허용한다")
    void allowsOnlyAppAndFrontendDevOrigins() {
        assertThat(prod.get("app.cors.allowed-origins")).isEqualTo("http://localhost:5173,https://localhost");
    }

    @Test
    @DisplayName("운영에서는 푸시(FCM)를 켠다")
    void enablesFcm() {
        assertThat(prod.get("tikkle.fcm.enabled")).isEqualTo("true");
    }
}
