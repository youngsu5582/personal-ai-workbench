package dev.joyson.aiworkbench.generation.domain

interface GeneratedFileRepository {

    fun saveAll(files: List<GeneratedFile>): List<GeneratedFile>

    /** Task 하나당 한 번씩 묻지 않으려고 한 번에 가져온다. */
    fun findAllByTaskIdIn(taskIds: Collection<Long>): List<GeneratedFile>
}
