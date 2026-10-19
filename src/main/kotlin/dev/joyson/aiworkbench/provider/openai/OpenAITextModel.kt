package dev.joyson.aiworkbench.provider.openai

/**
 * 텍스트 어댑터가 다루는 모델 목록.
 *
 * 이미지 모델([OpenAIImageModel])과 섞지 않는다. 같은 OpenAI 라도 이미지 모델은 Responses API 로
 * 글을 만들지 않고, 텍스트 모델은 Images API 를 받지 않는다 — 목록이 하나면 `supports` 가 그 차이를 답하지 못한다.
 *
 * 여기 없는 모델은 `supports` 가 false 를 답한다. 모델은 경로를 가르지 않는다 — 모두 `/v1/responses` 이고
 * 세대는 body 의 `model` 로 말한다.
 */
enum class OpenAITextModel(val modelName: String) {
    /** 미검증. 문서가 새 프로젝트에 권하는 모델이고, 단가가 가장 비싸다. */
    GPT_6_ASTRA("gpt-6-astra"),

    /** 미검증. */
    GPT_6_1_SOL("gpt-6.1-sol"),

    /**
     * 실제 호출로 확인함(2026-10-18) — 형식 없는 글, strict JSON Schema 가 스키마의 키 순서대로 답하는 것까지.
     * 단가가 가장 싸다.
     */
    GPT_6_LUNA("gpt-6-luna"),

    /** 실제 호출로 확인함(2026-10-18) — luna 와 같은 두 가지. 5.6 세대다. */
    GPT_5_6_TERRA("gpt-5.6-terra"),
    ;

    companion object {
        fun find(modelName: String): OpenAITextModel? =
            entries.firstOrNull { it.modelName == modelName }
    }
}
