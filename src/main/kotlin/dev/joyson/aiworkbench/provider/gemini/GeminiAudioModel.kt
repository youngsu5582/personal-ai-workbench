package dev.joyson.aiworkbench.provider.gemini

/**
 * 음성 어댑터가 다루는 모델 목록. 여기 없는 모델은 `supports` 가 false 를 답한다.
 *
 * 모두 같은 경로(Interactions)이고 모델은 본문의 `model` 로 말한다. 목소리 목록은 같다.
 */
enum class GeminiAudioModel(val modelName: String) {
    /**
     * 실제 호출로 확인함(2026-11-22) — 단계 목록(말투 주석 있음 · 없음), `audio/wav` 지정, usage 의 칸 이름,
     * `data` 청크로 잰 길이와 출력 토큰(초당 25)의 일치. 2027-01-01 부터 단가가 두 배다.
     */
    GEMINI_3_8_FLASH_TTS("gemini-3.8-flash-tts"),

    /** 출력 단가가 [GEMINI_3_8_FLASH_TTS] 의 2/3 다. 미검증 — 실제로 부른 적이 없다. */
    GEMINI_3_8_FLASH_LITE_TTS("gemini-3.8-flash-lite-tts"),
    ;

    companion object {
        fun find(modelName: String): GeminiAudioModel? =
            entries.firstOrNull { it.modelName == modelName }
    }
}
