package dev.joyson.aiworkbench.provider.openai

/**
 * 판정 어댑터가 다루는 모델 목록.
 *
 * 텍스트 모델([OpenAITextModel])과 이름이 겹쳐도 목록을 나눈다. 같은 이름이라도 Decisions API 를 받는지는
 * 따로 확인할 사실이고, 목록이 하나면 `supports` 가 그 차이를 답하지 못한다.
 *
 * 여기 없는 모델은 `supports` 가 false 를 답한다. 모델은 경로를 가르지 않는다 — 모두 `/v1/decisions` 다.
 */
enum class OpenAIDecisionModel(val modelName: String) {
    /**
     * 실제 호출로 확인함(2026-10-31) — 글 근거, 바이트로 실은 이미지 근거, 세 질문 종류(predicate · choice · score).
     * `usage` 의 `output_tokens` 는 0 이라 같은 모델의 텍스트 단가표가 입력 단가만 매긴다.
     */
    GPT_6_LUNA("gpt-6-luna"),
    ;

    companion object {
        fun find(modelName: String): OpenAIDecisionModel? =
            entries.firstOrNull { it.modelName == modelName }
    }
}
