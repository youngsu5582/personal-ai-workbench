package dev.joyson.aiworkbench.provider.openai

/**
 * OpenAI 가 `size` 에 쓰는 표기. `1024x1024` 처럼 가로x세로다.
 *
 * 보낼 때 만드는 쪽과 받을 때 푸는 쪽이 이 타입 하나다. 표기 규칙이 한 곳에 있어야
 * 보내는 표기와 읽는 표기가 어긋나지 않는다.
 *
 * OpenAI 의 어휘라 포트로 올리지 않는다. 다른 Provider 는 자기 표기를 갖는다.
 */
data class OpenAISize(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0 && height > 0) { "가로·세로는 양수여야 한다" }
    }

    /** 요청 body 에 실리는 표기. `toString` 은 로그용으로 두고 이것과 묶지 않는다. */
    val notation: String get() = "${width}x$height"

    companion object {
        /**
         * 표기를 푼다. 모양이 다르면 null — 못 읽는 것은 예외가 아니라 모르는 것이다.
         *
         * 저쪽이 언제든 표기를 바꿀 수 있어서, 못 읽었다고 호출을 실패시키지 않는다.
         * 무엇으로 물러설지는 부르는 쪽이 정한다.
         */
        fun parseOrNull(text: String?): OpenAISize? {
            val parts = text?.split("x")?.takeIf { it.size == 2 } ?: return null
            val width = parts[0].trim().toIntOrNull() ?: return null
            val height = parts[1].trim().toIntOrNull() ?: return null
            // 생성자의 require 보다 먼저 거른다. 거기서 터지면 null 로 물러선다는 약속이 깨진다.
            return if (width > 0 && height > 0) OpenAISize(width, height) else null
        }
    }
}
