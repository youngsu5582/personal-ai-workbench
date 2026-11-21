package dev.joyson.aiworkbench.provider.gemini

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

/**
 * Gemini `generateContent` 전송 담당. Gemini 의 HTTP 계약만 알고 우리 도메인 타입을 import 하지 않는다.
 *
 * Google 이 새 프로젝트에 권하는 것은 Interactions API 지만 이쪽을 쓴다. 답이 왜 멈췄는지를
 * `finishReason` · `promptFeedback.blockReason` 으로 말해 주는 것이 이쪽뿐이고, 포트의 `StopReason` 이 그것을 요구한다.
 *
 * 요청을 저쪽 로그에 남길지(`store`)는 보내지 않아 키를 만든 프로젝트의 설정을 따른다.
 * 2026-10-26 이후 만든 유료 프로젝트는 기본으로 남기고, 그 전 프로젝트와 Free Tier 는 남기지 않는다.
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
