package dev.joyson.aiworkbench.generation.infrastructure

import dev.joyson.jooq.tables.references.GENERATED_FILES
import dev.joyson.jooq.tables.references.GENERATION_JOBS
import dev.joyson.jooq.tables.references.GENERATION_JOB_TASKS
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Repository
import java.util.UUID

/** 내려보내는 데 필요한 것만. 엔티티를 통째로 읽을 이유가 없다. */
data class FileLocation(
    val storageKey: String,
    val mimeType: String,
)

/**
 * 파일을 **소유자와 함께** 찾는다.
 *
 * JPA 리포지토리가 아니라 jOOQ 인 이유가 둘 있다.
 *
 * 하나는 `generated_files` 에 job 도 owner 도 없고 `task_id` 뿐이라, 소유자까지 가려면
 * 두 단계를 타고 올라가야 한다는 것이다. 모듈 안에서도 Aggregate 를 ID 로 참조하기로 했으므로
 * 연관관계가 없고, 그러면 메서드 이름으로 만드는 파생 쿼리가 닿지 못한다.
 *
 * 다른 하나는 `mimeType` 이 JSON 컬럼 안에 있다는 것이다. 엔티티로 읽으면 metadata 를 통째로
 * 역직렬화해야 하는데, 필요한 건 문자열 하나다. 여기서는 DB 가 꺼내 준다.
 */
@Repository
class GeneratedFileFinder(
    private val dsl: DSLContext,
) {

    /**
     * JSON 컬럼에서 형식만 꺼낸다.
     *
     * 생성된 코드는 이 컬럼을 `JSON` 으로 안다 — 스키마를 마이그레이션 SQL 에서 읽을 때
     * jOOQ 가 H2 로 해석하며 `jsonb` 를 `json` 으로 접기 때문이다. 그래서 타입 있는 헬퍼 대신
     * 식을 직접 적는다. `->>` 는 PostgreSQL 문법이고, 이 프로젝트는 PostgreSQL 만 쓴다.
     */
    private val mimeType: Field<String> =
        DSL.field("{0} ->> 'mimeType'", SQLDataType.VARCHAR, GENERATED_FILES.METADATA)

    fun findOwned(fileUuid: UUID, ownerUuid: UUID): FileLocation? =
        dsl.select(GENERATED_FILES.STORAGE_KEY, mimeType)
            .from(GENERATED_FILES)
            .join(GENERATION_JOB_TASKS).on(GENERATION_JOB_TASKS.ID.eq(GENERATED_FILES.TASK_ID))
            .join(GENERATION_JOBS).on(GENERATION_JOBS.ID.eq(GENERATION_JOB_TASKS.JOB_ID))
            .where(GENERATED_FILES.UUID.eq(fileUuid))
            .and(GENERATION_JOBS.OWNER_USER_UUID.eq(ownerUuid))
            .fetchOne()
            ?.let { (storageKey, mimeType) ->
                FileLocation(storageKey = storageKey!!, mimeType = mimeType!!)
            }
}
