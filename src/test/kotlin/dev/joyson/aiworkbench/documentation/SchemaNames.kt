package dev.joyson.aiworkbench.documentation

import dev.joyson.jooq.DefaultCatalog

/**
 * 마이그레이션을 끝까지 적용한 스키마의 테이블과 컬럼.
 *
 * jOOQ 가 마이그레이션에서 만든 코드에서 읽는다. 마이그레이션 SQL 을 직접 검색하면
 * 지운 컬럼도 옛 파일에 남아 있어 통과해 버린다.
 */
internal class SchemaNames(private val tables: Map<String, Set<String>>) {
    private val columns = tables.values.flatten().toSet()

    fun columnsOf(table: String): Set<String>? = tables[table]

    fun knows(name: String) = name in tables || name in columns

    companion object {
        fun current() = SchemaNames(
            DefaultCatalog.DEFAULT_CATALOG.schemas
                .flatMap { it.tables }
                .associate { t -> t.name to t.fields().map { it.name }.toSet() },
        )
    }
}
