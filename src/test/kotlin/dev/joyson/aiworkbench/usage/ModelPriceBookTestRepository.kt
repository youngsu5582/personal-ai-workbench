package dev.joyson.aiworkbench.usage

import dev.joyson.aiworkbench.usage.domain.ModelPriceBook
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 테스트가 단가표를 넣는 길.
 *
 * 애플리케이션의 포트에는 `save` 가 없다 — 단가표는 마이그레이션으로만 들어온다.
 * 테스트 DB 는 마이그레이션이 아니라 엔티티로 스키마를 만들어 V10 의 단가가 없으므로, 필요한 표를 여기서 넣는다.
 */
interface ModelPriceBookTestRepository : JpaRepository<ModelPriceBook, Long>
