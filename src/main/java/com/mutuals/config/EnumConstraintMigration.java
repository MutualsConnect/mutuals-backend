package com.mutuals.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hibernate crea una restricción CHECK con los valores de cada enum, pero con {@code ddl-auto=update} no
 * la actualiza cuando se agrega un valor nuevo. En una base de datos que ya existía, insertar
 * {@code InteractionMethod.PHOTO} o {@code NotificationType.MOMENT_STARTED} fallaría.
 *
 * <p>Al iniciar, en PostgreSQL, se eliminan esas dos restricciones (de forma idempotente). La validez
 * de los valores la sigue garantizando el enum de la aplicación. Cuando el proyecto adopte migraciones
 * versionadas (Flyway), esta clase puede reemplazarse por una migración.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnumConstraintMigration implements ApplicationRunner {

    private record Constraint(String table, String name) {
    }

    private static final List<Constraint> STALE = List.of(
            new Constraint("interactions", "interactions_method_check"),
            new Constraint("notifications", "notifications_type_check"));

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        String product = jdbcTemplate.execute((ConnectionCallback<String>) connection ->
                connection.getMetaData().getDatabaseProductName());
        if (!"PostgreSQL".equalsIgnoreCase(product)) {
            return;
        }
        for (Constraint constraint : STALE) {
            jdbcTemplate.execute("ALTER TABLE IF EXISTS %s DROP CONSTRAINT IF EXISTS %s"
                    .formatted(constraint.table(), constraint.name()));
        }
        log.info("Enum CHECK constraints refreshed for new InteractionMethod / NotificationType values");
    }
}
