package dev.joyson.aiworkbench.generation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * 서비스에서 반복해 사용할 시스템 프롬프트 템플릿이다.
 *
 * [systemPrompt]에는 실행 시 채울 변수 자리를 둘 수 있다. 사용자 프롬프트와 모델은
 * 실행 요청에서 받으므로 여기에 저장하지 않는다. 실행할 때 완성한 지시는 Job에 저장되어
 * 이후 이 템플릿을 수정하거나 삭제해도 이미 접수된 Job의 입력은 바뀌지 않는다.
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
    /** 변수 치환 전의 시스템 지시문. 변수는 `{{role}}`처럼 이름을 중괄호 두 쌍으로 감싼다. */
    @Column(name = "instructions", nullable = false, columnDefinition = "text")
    var systemPrompt: String,
    /** 처음 저장한 시각. 목록을 최신순으로 보여줄 때 사용한다. */
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
