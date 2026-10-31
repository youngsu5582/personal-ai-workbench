package dev.joyson.aiworkbench.provider.openai

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

/**
 * OpenAI Decisions API 전송 담당.
 *
 * [OpenAITextClient] 와 같은 자리다 — OpenAI 의 HTTP 계약만 알고 우리 도메인 타입을 import 하지 않는다.
 * 실패 해석도 같은 [readOrThrow] 를 쓴다.
 */
class OpenAIDecisionClient(
    private val restClient: RestClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun decide(request: OpenAIDecisionRequest): OpenAIDecisionResponse {
        // 근거는 남기지 않는다. 사용자의 글과 이미지다.
        log.debug("판정을 요청한다: model={} 질문={}개", request.model, request.questions.size)

        val startedAt = System.nanoTime()
        try {
            return restClient.post()
                .uri(DECISIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .readOrThrow(OpenAIDecisionResponse::class.java)
        } finally {
            // 실패했을 때도 남긴다 — 타임아웃이 의심되는 순간에 필요한 숫자가 바로 이것이다.
            log.info("판정 호출이 끝났다. model={} 소요={}ms", request.model, (System.nanoTime() - startedAt) / 1_000_000)
        }
    }

    companion object {
        const val DECISIONS_PATH = "/v1/decisions"
    }
}
