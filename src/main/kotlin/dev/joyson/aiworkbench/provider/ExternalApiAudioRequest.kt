package dev.joyson.aiworkbench.provider

/**
 * TTS 로 음성을 만들 글 한 편.
 *
 * 글의 길이 상한은 여기서 보지 않는다. 모델마다 다르고, 넘으면 저쪽이 4xx 로 거절한다.
 */
data class ExternalApiAudioRequest(
    /** 그대로 소리 내어 읽힌다. 글 속의 지시("밝게 읽어:")도 읽히므로 말투는 [style] 로 준다. */
    val text: String,

    /** 목소리 이름. 받을 수 있는 이름은 어댑터가 밝힌다([ExternalApiAudioProvider.voices]). */
    val voice: String,

    /** 어떻게 읽을지("차분하고 또렷하게"). 없으면 말투 지시를 보내지 않는다. */
    val style: String? = null,
) {
    init {
        require(text.isNotBlank()) { "음성으로 만들 글이 비어 있다" }
        require(voice.isNotBlank()) { "목소리가 비어 있다" }
    }
}
