package dev.joyson.aiworkbench.usage.domain

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * 구현은 Spring Data 가 만든다.
 *
 * `save` 가 없다. 단가표는 마이그레이션으로만 들어온다.
 */
interface ModelPriceBookRepository : Repository<ModelPriceBook, Long> {

    /**
     * [at] 에 적용되는 표. `effectiveFrom <= at < effectiveTo` 다.
     *
     * 하나가 아니라 목록을 돌려준다. 기간 겹침을 DB 가 막지 않으므로 둘이 나올 수 있고,
     * 그때 어느 쪽을 고를지는 부르는 쪽이 정한다 — 여기서 예외를 던지면 그 호출은 영영 계산되지 않는다.
     */
    @Query(
        """
        select b from ModelPriceBook b
        where b.provider = :provider
          and b.model = :model
          and b.effectiveFrom <= :at
          and (b.effectiveTo is null or b.effectiveTo > :at)
        """,
    )
    fun findEffective(
        @Param("provider") provider: String,
        @Param("model") model: String,
        @Param("at") at: Instant,
    ): List<ModelPriceBook>
}
