package dev.joyson.aiworkbench.usage.infrastructure

import dev.joyson.aiworkbench.usage.domain.ProviderCall
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

interface ProviderCallRepository : JpaRepository<ProviderCall, Long> {

    fun findAllByTaskUuid(taskUuid: UUID): List<ProviderCall>

    fun findAllByJobUuid(jobUuid: UUID): List<ProviderCall>

    /**
     * 기간 안의 지출을 모델별로 접는다.
     *
     * 세는 일을 DB 에 시킨다 — 행을 다 끌어와 메모리에서 접으면 `usageRaw` 까지 딸려 온다.
     * 집계에 쓰지도 않을 JSON 을 행마다 역직렬화할 이유가 없다.
     *
     * `count` 로만 세고 `sum(정수)` 를 쓰지 않는다. `count` 는 어떤 DB 에서도 bigint 를 돌려주지만
     * 정수 리터럴의 합은 타입이 방언마다 갈려, 프로젝션이 런타임에 어긋난다.
     *
     * `count(c.cost.usd)` 는 **null 이 아닌 행만** 센다. 이 값이 [ModelUsageRow.calls] 보다 작으면
     * 비용 합계가 전체를 말하지 않는다는 뜻이고, 그 사실은 응답까지 그대로 올라가야 한다.
     */
    @Query(
        """
        select c.provider as provider,
               c.model as model,
               count(c) as calls,
               count(case when c.succeeded = true then 1 else null end) as succeededCalls,
               count(c.cost.usd) as costKnownCalls,
               sum(c.cost.usd) as costUsd
        from ProviderCall c
        where c.ownerUserUuid = :owner
          and c.calledAt >= :from
          and c.calledAt < :to
        group by c.provider, c.model
        order by c.model asc
        """,
    )
    fun summarizeByModel(
        @Param("owner") owner: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): List<ModelUsageRow>
}

/** [ProviderCallRepository.summarizeByModel] 의 한 줄. 별칭 이름이 곧 이 프로퍼티 이름이다. */
interface ModelUsageRow {
    val provider: String
    val model: String
    val calls: Long
    val succeededCalls: Long

    /** 비용을 아는 호출 수. [calls] 보다 작으면 [costUsd] 는 전체가 아니다. */
    val costKnownCalls: Long

    /** 아는 것만 더한 값. 하나도 모르면 null 이다. */
    val costUsd: BigDecimal?
}
