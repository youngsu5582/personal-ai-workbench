package dev.joyson.aiworkbench.provider.openai

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

/**
 * OpenAI Responses API 전송 담당.
 *
 * [OpenAIImageClient] 와 같은 자리다 — OpenAI 의 HTTP 계약만 알고 우리 도메인 타입을 import 하지 않는다.
 * 실패 해석도 그쪽과 같은 [readOrThrow] 를 쓴다.
 *
 * 이 API 도 **동기**로 부른다. 추론 모델은 답하기 전에 오래 생각할 수 있어 읽기 타임아웃은 이미지와 같은 값을 쓴다.
 */
class OpenAITextClient(
    private val restClient: RestClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun respond(request: OpenAIResponseRequest): OpenAIResponse {
        // 프롬프트는 남기지 않는다. 사용자의 글이고, 길이를 Provider 가 아니라 사용자가 정한다.
        log.debug("글 생성을 요청한다: model={} 스키마={}", request.model, request.text != null)

        val startedAt = System.nanoTime()
        try {
            return restClient.post()
                .uri(RESPONSES_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .readOrThrow(OpenAIResponse::class.java)
        } finally {
            // 실패했을 때도 남긴다 — 타임아웃이 의심되는 순간에 필요한 숫자가 바로 이것이다.
            log.info("글 생성 호출이 끝났다. model={} 소요={}ms", request.model, (System.nanoTime() - startedAt) / 1_000_000)
        }
    }

    companion object {
        /** 모델·형식과 무관하게 하나다. 세대는 body 의 `model` 로 말한다. */
        const val RESPONSES_PATH = "/v1/responses"
    }
}
