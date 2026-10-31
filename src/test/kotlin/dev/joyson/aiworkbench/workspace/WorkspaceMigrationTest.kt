package dev.joyson.aiworkbench.workspace

import dev.joyson.aiworkbench.IntegrationTest
import org.flywaydb.core.Flyway
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkspaceMigrationTest @Autowired constructor(private val dataSource: DataSource) : IntegrationTest() {
    @Test
    fun `기존 사용자마다 기본 서비스를 하나 만든다`() {
        val schema = "workspace_test_" + UUID.randomUUID().toString().replace("-", "")
        val owner = UUID.randomUUID()
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).target("14").load().migrate()
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """INSERT INTO "$schema".users (uuid, display_name, status, created_at, updated_at)
                       VALUES (?, '기존 사용자', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""",
                ).use { statement ->
                    statement.setObject(1, owner)
                    statement.executeUpdate()
                }
            }
            Flyway.configure().dataSource(dataSource).schemas(schema).load().migrate()
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """SELECT owner_user_uuid, is_default FROM "$schema".workspaces""",
                ).use { statement ->
                    statement.executeQuery().use { rows ->
                        assertTrue(rows.next())
                        assertEquals(owner, rows.getObject(1, UUID::class.java))
                        assertTrue(rows.getBoolean(2))
                        assertEquals(false, rows.next())
                    }
                }
            }
        } finally {
            dataSource.connection.use { connection ->
                connection.createStatement().use { it.execute("""DROP SCHEMA IF EXISTS "$schema" CASCADE""") }
            }
        }
    }
}
