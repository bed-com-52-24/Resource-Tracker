package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

/** Repairs databases where team_projects still points at the legacy UUID projects.id key. */
class V59__repair_team_projects_integer_foreign_key : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val connection = context.connection
        val projectColumnType = columnType(connection, "team_projects", "project_id") ?: return
        val referencedColumn = referencedProjectColumn(connection)

        if (projectColumnType.startsWith("int") && referencedColumn == "project_id") return

        val sourceColumn = referencedColumn
            ?: if (projectColumnType.startsWith("int")) {
                "project_id"
            } else if (columnType(connection, "projects", "id") != null) {
                "id"
            } else {
                "project_id"
            }
        require(sourceColumn == "id" || sourceColumn == "project_id") {
            "Unexpected team_projects foreign key target: projects.$sourceColumn"
        }

        execute(connection, "DROP TABLE IF EXISTS team_projects_v59")
        execute(
            connection,
            """
            CREATE TABLE team_projects_v59 (
                team_id INT NOT NULL,
                project_id INT NOT NULL,
                PRIMARY KEY (team_id, project_id),
                CONSTRAINT fk_team_projects_v59_team
                    FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE,
                CONSTRAINT fk_team_projects_v59_project
                    FOREIGN KEY (project_id) REFERENCES projects(project_id)
            )
            """.trimIndent(),
        )

        execute(
            connection,
            "INSERT INTO team_projects_v59 (team_id, project_id) " +
                "SELECT tp.team_id, p.project_id FROM team_projects tp " +
                "JOIN projects p ON p.$sourceColumn = tp.project_id",
        )

        val oldCount = rowCount(connection, "team_projects")
        val newCount = rowCount(connection, "team_projects_v59")
        if (oldCount != newCount) {
            execute(connection, "DROP TABLE team_projects_v59")
            error("Cannot migrate team_projects safely: mapped $newCount of $oldCount project links")
        }

        execute(connection, "DROP TABLE team_projects")
        execute(connection, "RENAME TABLE team_projects_v59 TO team_projects")
    }

    private fun referencedProjectColumn(connection: java.sql.Connection): String? =
        connection.prepareStatement(
            "SELECT referenced_column_name FROM information_schema.key_column_usage " +
                "WHERE table_schema = DATABASE() AND table_name = 'team_projects' " +
                "AND column_name = 'project_id' AND referenced_table_name = 'projects' LIMIT 1",
        ).use { statement ->
            statement.executeQuery().use { result ->
                if (result.next()) result.getString(1) else null
            }
        }

    private fun columnType(connection: java.sql.Connection, table: String, column: String): String? =
        connection.prepareStatement(
            "SELECT column_type FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
        ).use { statement ->
            statement.setString(1, table)
            statement.setString(2, column)
            statement.executeQuery().use { result ->
                if (result.next()) result.getString(1).lowercase() else null
            }
        }

    private fun rowCount(connection: java.sql.Connection, table: String): Long =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM $table").use { result ->
                result.next()
                result.getLong(1)
            }
        }

    private fun execute(connection: java.sql.Connection, sql: String) {
        connection.createStatement().use { it.execute(sql) }
    }
}
