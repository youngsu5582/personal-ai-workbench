package dev.joyson.aiworkbench.generation.domain

import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * 결과물 하나에 대해 **Provider 가 추가로 알려준 것**.
 *
 * [FileMetadata] 와 가르는 기준은 **바이트에서 나오는가**다. 크기·형식·용량은 파일을 열면
 * 알 수 있고 다른 곳으로 옮겨도 따라가지만, 여기 있는 값들은 저쪽이 말해줘야만 안다.
 * 우리가 계산해 채울 수 있는 것은 여기 들어오지 않는다.
 *
 * 호출 단위가 아니라 **결과물 단위**다. 한 호출이 여러 장을 내면 장마다 따로 온다 —
 * 호출 단위의 사실은 `provider_calls` 가 가진다.
 *
 * 아무것도 답해주지 않는 Provider 도 있어 통째로 null 일 수 있다.
 */
data class ProviderInfo(
    /**
     * 모델이 다시 쓴 프롬프트.
     *
     * **`gpt-image-2` 는 주지 않는다**(2026-09-24 실호출로 확인). 주는 Provider 를 위해 자리는 둔다.
     *
     * 우리가 보낸 문장이 아니라 **이것이 실제 입력**이다. 결과가 기대와 다를 때 그 이유가
     * 여기서 드러나고, 같은 프롬프트로 다시 만들어도 결과가 달라지는 이유이기도 하다.
     */
    val revisedPrompt: String? = null,

    /**
     * Provider 가 이 결과물에 붙인 식별자.
     *
     * 저쪽 청구서·지원 문의와 대조할 때 쓴다. 우리 uuid 는 저쪽이 모르고, 저쪽 id 는
     * 안 적으면 다시 알 길이 없다 — 응답은 한 번뿐이다.
     *
     * `generation_job_tasks.provider_task_id` 가 아니라 여기 둔다 — 그 자리는 폴링용 **작업**
     * 식별자고 이것은 **결과물** 식별자다.
     */
    val providerId: String? = null,
) {
    /**
     * 아무것도 안 담긴 객체를 행에 남기지 않기 위해 쓴다. 빈 칸과 "없다" 는 다른 뜻이어야 한다.
     *
     * `@get:JsonIgnore` 가 없으면 이 값이 `empty` 라는 이름으로 행에 박히고, 되읽을 때
     * 선언에 없는 필드라 역직렬화가 통째로 실패한다. 파생값은 저장하지 않는다.
     */
    @get:JsonIgnore
    val isEmpty: Boolean get() = revisedPrompt == null && providerId == null
}
