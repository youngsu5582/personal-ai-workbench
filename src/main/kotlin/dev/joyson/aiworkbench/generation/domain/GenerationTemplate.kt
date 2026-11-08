package dev.joyson.aiworkbench.generation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * 서비스에서 반복해 쓰는 생성 요청이다.
 *
 * 엔티티는 누구의 어느 서비스에 속한 무슨 이름의 템플릿인지만 갖고, 실행 규칙은 [spec] 이 갖는다.
 * 실행할 때 완성한 옵션은 Job 에 저장되므로, 이후 이 템플릿을 고치거나 지워도 이미 접수된 Job 의 입력은 바뀌지 않는다.
 */
@Entity
@Table(name = "generation_templates")
class GenerationTemplate(
    /** DB 내부 식별자. 외부에는 [uuid]를 사용한다. */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    /** 템플릿을 외부에서 식별하는 값. */
    @Column(nullable = false, unique = true, updatable = false)
    val uuid: UUID = UUID.randomUUID(),
    /** 템플릿을 소유한 사용자의 UUID. 사용자 모듈과 FK나 엔티티 연관관계를 만들지 않는다. */
    @Column(name = "owner_user_uuid", nullable = false, updatable = false)
    val ownerUserUuid: UUID,
    /** 템플릿이 속한 서비스의 UUID. 조회와 실행은 이 서비스 범위로 제한한다. */
    @Column(name = "workspace_uuid", nullable = false, updatable = false)
    val workspaceUuid: UUID,
    /** 사용자가 템플릿을 알아볼 수 있는 이름. */
    @Column(nullable = false)
    var name: String,
    /** 실행 명세. 종류는 만든 뒤 바꾸지 않는다([replace]). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    var spec: TemplateSpec,
    /** 처음 저장한 시각. 목록을 최신순으로 보여줄 때 사용한다. */
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    /** 이름과 명세를 바꾼다. 종류가 다른 명세면 [IllegalArgumentException] 이다. */
    fun replace(name: String, spec: TemplateSpec) {
        require(spec.option.type == this.spec.option.type) { "템플릿의 종류는 바꿀 수 없다: ${this.spec.option.type.value}" }
        this.name = name
        this.spec = spec
    }
}
