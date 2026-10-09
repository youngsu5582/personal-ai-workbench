package dev.joyson.aiworkbench.generation.infrastructure

import dev.joyson.aiworkbench.generation.domain.GeneratedFile
import dev.joyson.aiworkbench.generation.domain.GeneratedFileRepository
import org.springframework.data.jpa.repository.JpaRepository

/** [GeneratedFileRepository] 의 구현. 실제 클래스는 Spring Data 가 만든다. */
interface JpaGeneratedFileRepository : JpaRepository<GeneratedFile, Long>, GeneratedFileRepository {

    /** 이어 주는 이유는 [JpaGenerationJobTaskRepository.saveAll] 과 같다. */
    override fun saveAll(files: List<GeneratedFile>): List<GeneratedFile> = saveAll<GeneratedFile>(files)

    fun findAllByTaskId(taskId: Long): List<GeneratedFile>
}
