package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.Connection
import java.sql.ResultSet

/**
 * Converts the remaining UUID-backed keys to integers. MySQL commits DDL statement by
 * statement, so each section checks the live schema and can resume after a failed attempt.
 */
class V22__integer_ids : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val connection = context.connection
        migrateProjects(connection)
        migrateResources(connection)
        migrateRefreshTokens(connection)
        migrateNotifications(connection)
    }

    private fun migrateProjects(connection: Connection) {
        if (columnExists(connection, "projects", "id")) {
            val teamProjectIdType = columnType(connection, "team_projects", "project_id")
            if (teamProjectIdType != null && !teamProjectIdType.startsWith("int")) {
                execute(
                    connection,
                    """
                    CREATE TABLE team_projects_new (
                        team_id INT NOT NULL,
                        project_id INT NOT NULL,
                        PRIMARY KEY (team_id, project_id),
                        CONSTRAINT fk_team_projects_new_team
                            FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE,
                        CONSTRAINT fk_team_projects_new_project
                            FOREIGN KEY (project_id) REFERENCES projects(project_id)
                    )
                    """.trimIndent(),
                )
                execute(
                    connection,
                    """
                    INSERT INTO team_projects_new (team_id, project_id)
                    SELECT tp.team_id, p.project_id
                    FROM team_projects tp
                    JOIN projects p ON p.id = tp.project_id
                    """.trimIndent(),
                )
                execute(connection, "DROP TABLE team_projects")
                execute(connection, "RENAME TABLE team_projects_new TO team_projects")
            }
            dropForeignKeyIfExists(connection, "resource_requests", "fk_request_project")
            if (indexExists(connection, "projects", "uq_projects_id")) {
                execute(connection, "ALTER TABLE projects DROP INDEX uq_projects_id")
            }
            execute(connection, "ALTER TABLE projects DROP COLUMN id")
        }

        if (!foreignKeyExists(connection, "resource_requests", "fk_request_project")) {
            execute(
                connection,
                "ALTER TABLE resource_requests ADD CONSTRAINT fk_request_project " +
                    "FOREIGN KEY (project_id) REFERENCES projects(project_id)",
            )
        }
    }

    private fun migrateResources(connection: Connection) {
        val resourceIdType = columnType(connection, "resources", "id") ?: return
        if (!resourceIdType.startsWith("binary")) return

        execute(connection, "ALTER TABLE resources ADD COLUMN resource_id INT NOT NULL AUTO_INCREMENT UNIQUE")
        dropForeignKeyIfExists(connection, "resource_inventory", "fk_inventory_resource")
        dropIndexIfExists(connection, "resource_inventory", "idx_inventory_resource")
        dropIndexIfExists(connection, "resource_inventory", "uq_resource_inventory_resource_location")
        execute(connection, "ALTER TABLE resource_inventory ADD COLUMN resource_id_new INT NULL")
        execute(
            connection,
            """
            UPDATE resource_inventory i
            JOIN resources r ON r.id = i.resource_id
            SET i.resource_id_new = r.resource_id
            """.trimIndent(),
        )
        execute(
            connection,
            "ALTER TABLE resource_inventory DROP COLUMN resource_id, " +
                "CHANGE COLUMN resource_id_new resource_id INT NOT NULL",
        )
        execute(
            connection,
            "ALTER TABLE resources DROP PRIMARY KEY, DROP COLUMN id, " +
                "CHANGE COLUMN resource_id id INT NOT NULL AUTO_INCREMENT, ADD PRIMARY KEY (id)",
        )
        execute(
            connection,
            "ALTER TABLE resource_inventory " +
                "ADD CONSTRAINT uq_resource_inventory_resource_location UNIQUE (resource_id, location), " +
                "ADD INDEX idx_inventory_resource (resource_id), " +
                "ADD CONSTRAINT fk_inventory_resource FOREIGN KEY (resource_id) REFERENCES resources(id)",
        )
    }

    private fun migrateRefreshTokens(connection: Connection) {
        val idType = columnType(connection, "refresh_tokens", "id") ?: return
        if (!idType.startsWith("binary")) return

        execute(connection, "DELETE FROM refresh_tokens")
        execute(
            connection,
            "ALTER TABLE refresh_tokens ADD COLUMN refresh_token_id INT NOT NULL AUTO_INCREMENT UNIQUE FIRST",
        )
        execute(
            connection,
            "ALTER TABLE refresh_tokens DROP PRIMARY KEY, DROP COLUMN id, " +
                "CHANGE COLUMN refresh_token_id id INT NOT NULL AUTO_INCREMENT, ADD PRIMARY KEY (id)",
        )
    }

    private fun migrateNotifications(connection: Connection) {
        val requestIdType = columnType(connection, "notifications", "request_id") ?: return
        if (requestIdType.contains("char")) {
            execute(
                connection,
                """
                UPDATE notifications
                SET request_id = CASE
                    WHEN request_id REGEXP '^[0-9]+$' THEN CAST(request_id AS UNSIGNED)
                    ELSE NULL
                END
                """.trimIndent(),
            )
            execute(
                connection,
                """
                UPDATE notifications n
                LEFT JOIN resource_requests rr ON rr.request_id = CAST(n.request_id AS UNSIGNED)
                SET n.request_id = NULL
                WHERE n.request_id IS NOT NULL AND rr.request_id IS NULL
                """.trimIndent(),
            )
            dropForeignKeyIfExists(connection, "notifications", "fk_notification_recipient")
            execute(
                connection,
                "ALTER TABLE notifications MODIFY COLUMN notification_id INT NOT NULL AUTO_INCREMENT, " +
                    "MODIFY COLUMN request_id INT NULL",
            )
        }

        if (!foreignKeyExists(connection, "notifications", "fk_notification_request")) {
            execute(
                connection,
                "ALTER TABLE notifications ADD CONSTRAINT fk_notification_request " +
                    "FOREIGN KEY (request_id) REFERENCES resource_requests(request_id)",
            )
        }
        if (!foreignKeyExists(connection, "notifications", "fk_notification_recipient")) {
            execute(
                connection,
                "ALTER TABLE notifications ADD CONSTRAINT fk_notification_recipient " +
                    "FOREIGN KEY (recipient_id) REFERENCES users(user_id)",
            )
        }
    }

    private fun execute(connection: Connection, sql: String) {
        connection.createStatement().use { it.execute(sql) }
    }

    private fun columnExists(connection: Connection, table: String, column: String): Boolean =
        columnType(connection, table, column) != null

    private fun columnType(connection: Connection, table: String, column: String): String? =
        connection.prepareStatement(
            "SELECT column_type FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
        ).use { statement ->
            statement.setString(1, table)
            statement.setString(2, column)
            statement.executeQuery().use { result -> if (result.next()) result.getString(1).lowercase() else null }
        }

    private fun indexExists(connection: Connection, table: String, index: String): Boolean =
        connection.prepareStatement(
            "SELECT 1 FROM information_schema.statistics " +
                "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ? LIMIT 1",
        ).use { statement ->
            statement.setString(1, table)
            statement.setString(2, index)
            statement.executeQuery().use(ResultSet::next)
        }

    private fun foreignKeyExists(connection: Connection, table: String, foreignKey: String): Boolean =
        connection.prepareStatement(
            "SELECT 1 FROM information_schema.table_constraints " +
                "WHERE constraint_schema = DATABASE() AND table_name = ? " +
                "AND constraint_name = ? AND constraint_type = 'FOREIGN KEY' LIMIT 1",
        ).use { statement ->
            statement.setString(1, table)
            statement.setString(2, foreignKey)
            statement.executeQuery().use(ResultSet::next)
        }

    private fun dropForeignKeyIfExists(connection: Connection, table: String, foreignKey: String) {
        if (foreignKeyExists(connection, table, foreignKey)) {
            execute(connection, "ALTER TABLE $table DROP FOREIGN KEY $foreignKey")
        }
    }

    private fun dropIndexIfExists(connection: Connection, table: String, index: String) {
        if (indexExists(connection, table, index)) {
            execute(connection, "ALTER TABLE $table DROP INDEX $index")
        }
    }
}
