package dev.joyson.aiworkbench.generation.domain

import org.springframework.data.repository.Repository

/** 구현은 Spring Data 가 만든다. */
interface GeneratedDecisionRepository : Repository<GeneratedDecision, Long> {

    fun save(decision: GeneratedDecision): GeneratedDecision

    /** Task 하나당 한 번씩 묻지 않으려고 한 번에 가져온다. */
    fun findAllByTaskIdIn(taskIds: Collection<Long>): List<GeneratedDecision>
}
