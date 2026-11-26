package com.tikkle;

import com.tikkle.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.BasicValuedModelPart;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB 스키마를 Flyway 마이그레이션이 만들고, Hibernate는 엔티티와 대조만 하는지 확인합니다.
 *
 * <p>Hibernate의 {@code validate}는 컬럼이 있는지와 타입만 보고 MySQL ENUM 컬럼의 값 목록은 비교하지 않습니다.
 * 엔티티 enum에 값을 더하고 마이그레이션을 빠뜨리면 그 값을 저장할 때 실패하므로, 여기서 값 목록을 따로 대조합니다.</p>
 */
class SchemaMigrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("스키마는 Flyway 기준 마이그레이션(V1)이 만든다")
    void schemaIsCreatedByBaselineMigration() {
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version = '1'");

        assertThat(history).hasSize(1);
        assertThat(history.getFirst().get("success")).isIn(true, 1, (byte) 1);
    }

    @Test
    @DisplayName("Hibernate는 스키마를 바꾸지 않고 엔티티와 대조만 한다")
    void hibernateOnlyValidatesSchema() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    @DisplayName("엔티티 enum의 모든 값이 DB ENUM 컬럼 정의에 있다")
    void everyEntityEnumValueExistsInDatabaseColumn() {
        List<EnumColumn> enumColumns = findEntityEnumColumns();
        List<String> problems = new ArrayList<>();

        for (EnumColumn column : enumColumns) {
            String columnType = findColumnType(column.table(), column.column());
            if (columnType == null) {
                problems.add(column + ": DB에 컬럼이 없다");
                continue;
            }
            if (!columnType.startsWith("enum(")) {
                continue;
            }
            List<String> databaseValues = parseEnumValues(columnType);
            for (Object constant : column.enumType().getEnumConstants()) {
                String value = ((Enum<?>) constant).name();
                if (!databaseValues.contains(value)) {
                    problems.add(column + ": DB ENUM에 '" + value + "'이 없다 (" + columnType + ")");
                }
            }
        }

        assertThat(enumColumns).as("엔티티에서 enum 컬럼을 찾지 못했다").isNotEmpty();
        assertThat(problems).isEmpty();
    }

    private List<EnumColumn> findEntityEnumColumns() {
        List<EnumColumn> columns = new ArrayList<>();
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        sessionFactory.getMappingMetamodel().forEachEntityDescriptor(entity ->
                entity.forEachAttributeMapping(attribute -> {
                    ModelPart part = attribute instanceof PluralAttributeMapping plural
                            ? plural.getElementDescriptor()
                            : attribute;
                    if (part instanceof BasicValuedModelPart basic) {
                        Class<?> javaType = basic.getJavaType().getJavaTypeClass();
                        if (javaType.isEnum()) {
                            columns.add(new EnumColumn(
                                    basic.getContainingTableExpression(), basic.getSelectionExpression(), javaType));
                        }
                    }
                }));
        return columns;
    }

    private String findColumnType(String table, String column) {
        List<String> types = jdbcTemplate.queryForList(
                "SELECT column_type FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                String.class, table, column);
        return types.isEmpty() ? null : types.getFirst();
    }

    private List<String> parseEnumValues(String columnType) {
        String inner = columnType.substring("enum(".length(), columnType.length() - 1);
        return Arrays.stream(inner.split(","))
                .map(value -> value.substring(1, value.length() - 1))
                .toList();
    }

    private record EnumColumn(String table, String column, Class<?> enumType) {

        @Override
        public String toString() {
            return table + "." + column + "(" + enumType.getSimpleName() + ")";
        }
    }
}
