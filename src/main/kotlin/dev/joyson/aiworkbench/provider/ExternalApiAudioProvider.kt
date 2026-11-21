package dev.joyson.aiworkbench.provider

/**
 * 글을 소리로 읽는 외부 API 한 곳을 감싸는 어댑터.
 *
 * 이미지 · 텍스트 · 판정 포트와 나눠 둔 이유는 같다 — 음성 모델은 이미지도 글도 만들지 않는다.
 * 포트가 갈려 있어야 찾는 일 자체가 종류를 묻는다.
 *
 * 규칙도 같다 — 표준 어휘만 알고, 구현체는 사실만 답한다.
 */
interface ExternalApiAudioProvider {

    /** 어느 외부 API 였는지 로그·메트릭에서 구분하기 위한 이름. 고르는 기준은 [supports] 다. */
    val name: String

    /** 이 어댑터가 다루는 모델 이름. 모델 목록 조회와 [supports] 가 같은 집합을 본다. */
    val models: Set<String>

    /** 이 모델로 소리를 만들 수 있는가. 사실 질의라 모르는 모델이면 예외가 아니라 false 다. */
    fun supports(model: String): Boolean = model in models

    /**
     * 이 모델이 읽을 수 있는 목소리 이름. 여기 없는 목소리는 부르지 않고 거절한다. 모르는 모델이면 빈 집합이다.
     *
     * 이름 목록의 정본은 어댑터다. 저쪽이 목록을 조회하는 API 를 주지 않아 저쪽 문서를 옮겨 적는다.
     * 기본값을 두지 않는 이유는 [ExternalApiTextProvider.efforts] 와 같다 — 어댑터를 더할 때마다 이 질문에 답하게 한다.
     */
    fun voices(model: String): Set<String>

    /**
     * 외부 API 호출 **1건**. `GenerationJobTask` 1행에 대응한다.
     *
     * 실패는 [ExternalApiException] 으로 알린다. 받은 소리의 길이를 읽지 못한 것도 실패다 —
     * 길이 없이 저장하지 않는다. 그때는 저쪽이 이미 만들어 과금했으므로 [FailureKind.REJECTED] 로 알리지 않는다.
     *
     * 다른 포트처럼 **동기**다.
     */
    fun generateAudio(model: String, request: ExternalApiAudioRequest): ExternalApiAudioResponse
}
