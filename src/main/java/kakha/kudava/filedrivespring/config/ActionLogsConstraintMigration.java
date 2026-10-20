package kakha.kudava.filedrivespring.config;

import jakarta.transaction.Transactional;
import kakha.kudava.filedrivespring.enums.ActionType;
import kakha.kudava.filedrivespring.enums.EntityType;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Hibernate creates check constraints for string-backed enums, but ddl-auto=update
 * does not expand an existing constraint when a new enum constant is introduced.
 * Keep the PostgreSQL constraints aligned before the application accepts traffic.
 */
@Component
public class ActionLogsConstraintMigration implements ApplicationRunner {

    private static final long MIGRATION_LOCK = 2_026_101_901L;

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public ActionLogsConstraintMigration(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                return;
            }
        }

        // Multiple ECS tasks may start together. Serialize this idempotent migration.
        jdbcTemplate.execute("select pg_advisory_xact_lock(" + MIGRATION_LOCK + ")");
        replaceConstraint("action_logs_action_check", "action", allowedValues(ActionType.values()));
        replaceConstraint("action_logs_entity_type_check", "entity_type", allowedValues(EntityType.values()));
    }

    private void replaceConstraint(String constraintName, String columnName, String allowedValues) {
        jdbcTemplate.execute("alter table action_logs drop constraint if exists " + constraintName);
        jdbcTemplate.execute("alter table action_logs add constraint " + constraintName
                + " check (" + columnName + " in (" + allowedValues + "))");
    }

    private static String allowedValues(Enum<?>[] values) {
        return Arrays.stream(values)
                .map(value -> "'" + value.name() + "'")
                .collect(Collectors.joining(", "));
    }
}
