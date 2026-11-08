package dev.joyson.aiworkbench.provider.gemini

/**
 * 텍스트 어댑터가 다루는 모델 목록. 여기 없는 모델은 `supports` 가 false 를 답한다.
 *
 * 모두 같은 경로(`generateContent`)이고 모델은 경로의 `{model}` 로 말한다.
 */
enum class GeminiTextModel(val modelName: String) {
    /**
     * 실제 호출로 확인함(2026-11-08) — 형식 없는 글, 스키마의 키 순서, `thinkingLevel` MINIMAL · HIGH.
     * 기본 thinking 은 medium 이라 짧은 답에도 생각 토큰이 답보다 수십 배 많을 수 있다. 2027-01-01 부터 단가가 두 배다.
     */
    GEMINI_3_6_FLASH("gemini-3.6-flash"),

    /** 실제 호출로 확인함(2026-11-08) — 3.6 Flash 와 같은 넷. 기본 thinking 은 minimal 이다. */
    GEMINI_3_5_FLASH_LITE("gemini-3.5-flash-lite"),
    ;

    companion object {
        fun find(modelName: String): GeminiTextModel? =
            entries.firstOrNull { it.modelName == modelName }
    }
}
