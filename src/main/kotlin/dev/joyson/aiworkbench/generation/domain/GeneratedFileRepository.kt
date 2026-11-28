package dev.joyson.aiworkbench.generation.domain

import org.springframework.data.repository.Repository

/** 구현은 Spring Data 가 만든다. */
interface GeneratedFileRepository : Repository<GeneratedFile, Long> {

    /** 파라미터가 `Iterable` 이어야 기본 구현의 `saveAll` 과 같은 메서드로 본다. */
    fun saveAll(files: Iterable<GeneratedFile>): List<GeneratedFile>

    /** Task 하나당 한 번씩 묻지 않으려고 한 번에 가져온다. */
    fun findAllByTaskIdIn(taskIds: Collection<Long>): List<GeneratedFile>

    /** 목록이 고른 파일을 한 번에 읽는다. 순서는 지키지 않으니 부르는 쪽이 식별자로 다시 줄 세운다. */
    fun findAllByIdIn(ids: Collection<Long>): List<GeneratedFile>
}
