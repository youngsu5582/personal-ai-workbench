package dev.joyson.aiworkbench.provider

/**
 * 모델 이름으로 그 모델을 다루는 어댑터를 찾아준다.
 *
 * 호출하는 쪽이 `"openai"` 같은 이름을 들고 있지 않아도 되게 하는 것이 이 클래스의 목적이다.
 * 모델만 주면 누가 처리할지는 여기서 정해진다 — 어떤 Provider 가 존재하는지는 호출하는 쪽의 관심사가 아니다.
 *
 * **사실만 답한다.** 처리할 수 있는 곳이 없으면 예외가 아니라 `null` 이다.
 * 404 를 줄지, 기본 모델로 갈아탈지, 대기열에 두고 나중에 볼지는 정책이라 호출하는 쪽이 정한다.
 *
 * 목록은 기동 시 한 번 고정된다. API 키가 없는 환경에서는 해당 어댑터의 빈이 아예 만들어지지 않으므로
 * (`OpenAIClientConfig` 의 조건부 등록), **설정과 가용성이 저절로 일치한다** — 따로 관리할 목록이 없다.
 */
class ProviderRegistry(
    private val providers: List<ExternalApiProvider>,
) {

    /**
     * 이 모델을 다룰 수 있는 어댑터. 없으면 `null`.
     *
     * 둘 이상이 같은 모델을 지원한다고 답하면 앞의 것을 쓴다.
     * 지금은 모델 이름이 Provider 마다 겹치지 않아 생기지 않는 상황이고,
     * 겹치기 시작하면(같은 공개 모델을 여러 곳이 서비스하는 경우) 모델 이름만으로는 고를 수 없어
     * `ProviderModel(provider, model)` 같은 식별자가 필요해진다.
     */
    fun find(model: String): ExternalApiProvider? = providers.firstOrNull { it.supports(model) }

    /** 지금 쓸 수 있는 어댑터 이름들. 진단·로그용이다. */
    val availableNames: List<String> get() = providers.map { it.name }
}
