package dev.joyson.aiworkbench.workspace

import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceOwnershipMigrationTest {
    @Test
    fun `기존 작업과 업로드와 사용량을 기본 서비스에 연결하며 파일 경로를 보존한다`() {
        PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine")).use { database ->
            database.start()
            fun flyway(target: String) = Flyway.configure()
                .dataSource(database.jdbcUrl, database.username, database.password)
                .locations("classpath:db/migration").target(target).load()
            flyway("15").migrate()
            val owner = UUID.randomUUID()
            val workspace = UUID.randomUUID()
            val key = "users/$owner/uploads/legacy.png"
            DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use { connection ->
                connection.createStatement().use { sql ->
                    sql.executeUpdate("""INSERT INTO users (uuid,display_name,status,created_at,updated_at)
                        VALUES ('$owner','기존 사용자','ACTIVE',now(),now())""")
                    sql.executeUpdate("""INSERT INTO workspaces (uuid,owner_user_uuid,name,is_default,created_at)
                        VALUES ('$workspace','$owner','기본 서비스',true,now())""")
                    sql.executeUpdate("""INSERT INTO generation_jobs (uuid,owner_user_uuid,option,model,task_count,status,created_at,updated_at)
                        VALUES ('${UUID.randomUUID()}','$owner','{}','model',1,'DISPATCHED',now(),now())""")
                    sql.executeUpdate("""INSERT INTO uploaded_files (owner_user_uuid,sha256,storage_key,metadata,status,created_at,updated_at)
                        VALUES ('$owner','${"a".repeat(64)}','$key','{}','READY',now(),now())""")
                    sql.executeUpdate("""INSERT INTO provider_calls (uuid,task_uuid,job_uuid,owner_user_uuid,provider,model,succeeded,latency_ms,called_at)
                        VALUES ('${UUID.randomUUID()}','${UUID.randomUUID()}','${UUID.randomUUID()}','$owner','fake','model',true,1,now())""")
                }
                flyway("16").migrate()
                for (table in listOf("generation_jobs", "uploaded_files", "provider_calls")) {
                    connection.createStatement().use { sql ->
                        sql.executeQuery("SELECT workspace_uuid FROM $table").use { rows ->
                            assertTrue(rows.next())
                            assertEquals(workspace, rows.getObject(1, UUID::class.java))
                        }
                    }
                }
                connection.createStatement().use { sql ->
                    sql.executeQuery("SELECT storage_key FROM uploaded_files").use { rows ->
                        assertTrue(rows.next())
                        assertEquals(key, rows.getString(1))
                    }
                }
            }
        }
    }
}
