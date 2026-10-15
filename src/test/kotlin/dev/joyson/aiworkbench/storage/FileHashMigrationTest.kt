package dev.joyson.aiworkbench.storage

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 실제 변환 도구의 백업·중복·과거 참조·유실 파일 제외를 검증한다. */
class FileHashMigrationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun convertsLegacyReferencesAndArchivesMissingFiles() {
        PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine")).use { db ->
            db.start()
            Flyway.configure().dataSource(db.jdbcUrl, db.username, db.password)
                .locations("filesystem:src/main/resources/db/migration").target("6").load().migrate()
            val owner = UUID.randomUUID()
            val generatedUuid = UUID.randomUUID()
            val uploadUuid = UUID.randomUUID()
            val duplicateUuid = UUID.randomUUID()
            val missingUuid = UUID.randomUUID()
            val image = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
            val hash = Sha256.of(image).hex
            val key = BlobKey.of(owner, Sha256.of(image), "image/png").value
            val local = directory.resolve("assets")
            Files.createDirectories(local)
            Files.write(local.resolve("legacy-upload.png"), image)
            Files.write(local.resolve("duplicate-upload.png"), image)
            DriverManager.getConnection(db.jdbcUrl, db.username, db.password).use { conn ->
                val createJob = """INSERT INTO generation_jobs (id,uuid,owner_user_uuid,option,model,task_count,status,created_at,updated_at) """
                val size = """"size":{"type":"ratio","ratio":"1:1","resolution":"1k"}"""
                conn.createStatement().use { s ->
                    listOf(
                        createJob + """VALUES (1,gen_random_uuid(),'$owner','{"type":"text-to-image","prompt":"x",$size}','fake',1,'CLOSED',now(),now())""",
                        """INSERT INTO generation_job_tasks (id,uuid,job_id,sequence,status,attempt_count,created_at) VALUES (1,gen_random_uuid(),1,0,'SUCCEEDED',1,now())""",
                        """INSERT INTO generated_files (id,uuid,task_id,storage_key,metadata,created_at) VALUES (1,'$generatedUuid',1,'$key','{"width":1,"height":1,"mimeType":"image/png","fileSize":8}',now())""",
                        """INSERT INTO uploaded_files (id,uuid,owner_user_uuid,storage_key,metadata,status,created_at,updated_at) VALUES
                           (1,'$uploadUuid','$owner','legacy-upload.png','{"mimeType":"image/png","fileSize":8}','READY',now(),now()),
                           (2,'$duplicateUuid','$owner','duplicate-upload.png','{"mimeType":"image/png","fileSize":8}','PENDING',now(),now()),
                           (3,'$missingUuid','$owner','missing.png','{"mimeType":"image/png","fileSize":8}','READY',now(),now())""",
                        createJob + """VALUES (2,gen_random_uuid(),'$owner','{"type":"image-to-image","prompt":"x","sources":[{"type":"generated","uuid":"$generatedUuid"},{"type":"uploaded","uuid":"$duplicateUuid"}],$size}','fake',1,'CLOSED',now(),now()),
                           (3,gen_random_uuid(),'$owner','{"type":"image-to-image","prompt":"x","sources":[{"type":"uploaded","uuid":"$missingUuid"}],$size}','fake',1,'CLOSED',now(),now())""",
                    ).forEach { s.executeUpdate(it) }
                }
                val envFile = directory.resolve(".env")
                Files.writeString(envFile, "DB_URL=" + db.jdbcUrl + "\nDB_USERNAME=" + db.username +
                    "\nDB_PASSWORD=" + db.password + "\nSTORAGE_PROVIDER=local\nSTORAGE_LOCAL_ROOT=" + local)
                fun run(vararg options: String): Pair<Int, String> {
                    val output = directory.resolve("output-" + UUID.randomUUID() + ".txt")
                    val process = ProcessBuilder(
                        listOf("python3", ".claude/scripts/migrate-file-hashes.py", "--env-file", envFile.toString()) + options,
                    ).redirectErrorStream(true).redirectOutput(output.toFile()).apply {
                        environment()["FILE_HASH_MIGRATION_CLASSPATH"] = System.getProperty("workbench.migration.classpath")
                    }.start()
                    assertTrue(process.waitFor(90, java.util.concurrent.TimeUnit.SECONDS))
                    return process.exitValue() to Files.readString(output)
                }
                val dryRun = run()
                assertTrue(dryRun.first != 0, dryRun.second)
                conn.createStatement().use { s ->
                    s.executeQuery("SELECT max(version::integer) FROM flyway_schema_history").use {
                        it.next(); assertEquals(6, it.getInt(1))
                    }
                }
                val report = directory.resolve("report")
                val applied = run("--apply", "--skip-missing", "--report-dir", report.toString())
                assertEquals(0, applied.first, applied.second)
                assertTrue(Files.size(report.resolve("database-before.dump")) > 0)
                assertTrue(Files.readString(report.resolve("before.json")).contains(missingUuid.toString()))
                conn.createStatement().use { s ->
                    s.executeQuery("SELECT sha256 FROM uploaded_files").use {
                        assertTrue(it.next()); assertEquals(hash, it.getString(1))
                        assertFalse(it.next(), "중복과 유실 업로드가 활성 테이블에 남았다")
                    }
                    s.executeQuery("SELECT option::text FROM generation_jobs WHERE id=2").use {
                        assertTrue(it.next()); assertFalse(it.getString(1).contains("\"uuid\""))
                        assertTrue(it.getString(1).contains(hash))
                    }
                    s.executeQuery("SELECT count(*) FROM generation_jobs WHERE id=3").use {
                        it.next(); assertEquals(0, it.getInt(1))
                    }
                    s.executeQuery("SELECT sha256 FROM generated_files").use {
                        assertTrue(it.next()); assertEquals(hash, it.getString(1))
                    }
                    s.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_name IN ('generated_files','uploaded_files') AND column_name='uuid'").use {
                        it.next(); assertEquals(0, it.getInt(1))
                    }
                }
                val repeated = run("--apply", "--skip-missing")
                assertEquals(0, repeated.first, repeated.second)
                assertTrue(repeated.second.contains("already_converted"))
            }
        }
    }
}
