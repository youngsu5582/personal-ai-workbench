package dev.joyson.aiworkbench.provider.gemini

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

/**
 * Gemini `generateContent` 전송 담당. Gemini 의 HTTP 계약만 알고 우리 도메인 타입을 import 하지 않는다.
 *
 * Google 이 새 프로젝트에 권하는 것은 Interactions API 지만 이쪽을 쓴다. 답이 왜 멈췄는지를
 * `finishReason` · `promptFeedback.blockReason` 으로 말해 주는 것이 이쪽뿐이고, 포트의 `StopReason` 이 그것을 요구한다.
 * 저쪽에 응답을 남기지도 않아 따로 끌 것이 없다.
 */
class GeminiTextClient(
    private val restClient: RestClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun generateContent(model: String, request: GeminiGenerateContentRequest): GeminiGenerateContentResponse {
        // 프롬프트는 남기지 않는다. 사용자의 글이다.
        log.debug("글 생성을 요청한다: model={} 스키마={}", model, request.generationConfig?.responseJsonSchema != null)

        val startedAt = System.nanoTime()
        try {
            return restClient.post()
                .uri(GENERATE_CONTENT_PATH, model)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .readOrThrow(GeminiGenerateContentResponse::class.java)
        } finally {
            log.info("글 생성 호출이 끝났다. model={} 소요={}ms", model, (System.nanoTime() - startedAt) / 1_000_000)
        }
    }

    companion object {
        /** 모델이 경로에 들어간다. 형식 · thinking 은 body 의 `generationConfig` 로 말한다. */
        const val GENERATE_CONTENT_PATH = "/v1beta/models/{model}:generateContent"

        const val API_KEY_HEADER = "x-goog-api-key"
    }
}
