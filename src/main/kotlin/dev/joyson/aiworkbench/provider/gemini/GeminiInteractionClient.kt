package dev.joyson.aiworkbench.provider.gemini

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

/**
 * Gemini Interactions API 전송 담당. Gemini 의 HTTP 계약만 알고 우리 도메인 타입을 import 하지 않는다.
 *
 * 글 생성이 쓰는 `generateContent`([GeminiTextClient])와 나눈 이유는 음성 모델이 이 API 로만 문서화돼 있어서다.
 * 인증 헤더와 실패 본문(`{"error": {"message", …}}`)은 같아 [readOrThrow] 를 함께 쓴다.
 */
class GeminiInteractionClient(
    private val restClient: RestClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun create(request: GeminiInteractionRequest): GeminiInteractionResponse {
        // 글은 남기지 않는다. 사용자의 글이다.
        log.debug("Interactions 를 요청한다: model={}", request.model)

        val startedAt = System.nanoTime()
        try {
            return restClient.post()
                .uri(INTERACTIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .readOrThrow(GeminiInteractionResponse::class.java)
        } finally {
            log.info("Interactions 호출이 끝났다. model={} 소요={}ms", request.model, (System.nanoTime() - startedAt) / 1_000_000)
        }
    }

    companion object {
        /** 모델은 경로가 아니라 본문의 `model` 로 말한다. */
        const val INTERACTIONS_PATH = "/v1beta/interactions"
    }
}
