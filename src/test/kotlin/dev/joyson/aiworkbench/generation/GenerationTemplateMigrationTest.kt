package dev.joyson.aiworkbench.generation

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
class GenerationTemplateMigrationTest @Autowired constructor(private val dataSource: DataSource) : IntegrationTest() {
    @Test
    fun `기존 템플릿은 소유자의 기본 서비스로 이관된다`() {
        val schema = "template_test_" + UUID.randomUUID().toString().replace("-", "")
        val owner = UUID.randomUUID()
        val workspace = UUID.randomUUID()
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).target("18").load().migrate()
            dataSource.connection.use { connection ->
                connection.prepareStatement("""INSERT INTO "$schema".workspaces (uuid, owner_user_uuid, name, is_default, created_at) VALUES (?, ?, '기본', true, CURRENT_TIMESTAMP)""").use {
                    it.setObject(1, workspace); it.setObject(2, owner); it.executeUpdate()
                }
                connection.prepareStatement("""INSERT INTO "$schema".generation_templates (uuid, owner_user_uuid, name, instructions, created_at) VALUES (?, ?, '이름', '지시', CURRENT_TIMESTAMP)""").use {
                    it.setObject(1, UUID.randomUUID()); it.setObject(2, owner); it.executeUpdate()
                }
            }
            Flyway.configure().dataSource(dataSource).schemas(schema).load().migrate()
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("""SELECT workspace_uuid FROM "$schema".generation_templates""").use { rows ->
                        assertTrue(rows.next())
                        assertEquals(workspace, rows.getObject(1, UUID::class.java))
                    }
                }
            }
        } finally {
            dataSource.connection.use { connection -> connection.createStatement().use { it.execute("""DROP SCHEMA IF EXISTS "$schema" CASCADE""") } }
        }
    }
}
