package dev.joyson.aiworkbench.upload

import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 빈 DB 뿐 아니라 기존 업로드 행을 가진 DB 도 새 버전으로 올라갈 수 있어야 한다. */
class UploadedFileMigrationTest {
    @Test
    fun `V6 의 기존 업로드를 보존하면서 V7 의 해시 제약을 더한다`() {
        PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine")).use { database ->
            database.start()
            fun flyway(target: String) = Flyway.configure()
                .dataSource(database.jdbcUrl, database.username, database.password)
                .locations("classpath:db/migration").target(target).load()

            flyway("6").migrate()
            DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use { connection ->
                val owner = UUID.randomUUID()
                fun insert(key: String, hash: String? = null) {
                    val withHash = if (hash == null) "" else ", sha256"
                    val hashValue = if (hash == null) "" else ", '$hash'"
                    connection.createStatement().use { it.executeUpdate(
                        """
                        INSERT INTO uploaded_files
                            (uuid, owner_user_uuid, storage_key, metadata, status, created_at, updated_at$withHash)
                        VALUES ('${UUID.randomUUID()}', '$owner', '$key', '{"mimeType":"image/png","fileSize":12}',
                            'READY', now(), now()$hashValue)
                        """,
                    ) }
                }
                insert("legacy-a")
                insert("legacy-b")
                flyway("7").migrate()
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT storage_key, sha256 FROM uploaded_files ORDER BY storage_key").use {
                        assertTrue(it.next())
                        assertEquals("legacy-a", it.getString("storage_key"))
                        assertNull(it.getString("sha256"))
                        assertTrue(it.next())
                        assertEquals("legacy-b", it.getString("storage_key"))
                        assertNull(it.getString("sha256"))
                    }
                }
                insert("new-key", "a".repeat(64))
                // 기존 null 은 공존하지만 신규 (owner, hash) 는 중복될 수 없다.
                val duplicate = runCatching { insert("other-key", "a".repeat(64)) }.exceptionOrNull()
                assertEquals("23505", (duplicate as java.sql.SQLException).sqlState)
            }
        }
    }
}
