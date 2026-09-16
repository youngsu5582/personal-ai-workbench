package dev.joyson.aiworkbench.provider.openai

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper

/**
 * OpenAI Images API 전송 담당.
 *
 * 이 클래스가 아는 것은 OpenAI 의 HTTP 계약뿐이다 — 우리 도메인 타입을 import 하지 않는다.
 * 그 규칙이 지켜지는 한 나중에 별도 모듈로 옮기는 일이 파일 이동으로 끝난다.
 *
 * 이 API 는 **동기**다. 호출하면 수십 초 뒤 이미지가 응답 본문에 담겨 온다 —
 * 작업 식별자를 받아 폴링하는 방식이 아니다. 그래서 읽기 타임아웃이 넉넉해야 한다.
 *
 * 경로를 상수로 갖지 않고 인자로 받는 이유는 **세대마다 경로가 다르기** 때문이다.
 * 어느 경로로 보낼지는 모델이 정하는 일이라 어댑터가 풀어서 넘긴다([OpenAIImageModel]).
 */
class OpenAIImageClient(
    private val restClient: RestClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 실패 본문을 원문으로 받아둔 뒤 파싱하기 위해 직접 읽는다. */
    private val mapper = jacksonObjectMapper()

    /**
     * @param path 보낼 엔드포인트 경로. `baseUrl` 기준 상대 경로다.
     */
    fun generate(path: String, request: OpenAIImageRequest): OpenAIImageResponse {
        log.info("이미지 생성을 요청한다: path={} model={} n={}", path, request.model, request.n)

        return restClient.post()
            .uri(path)
            .body(request)
            .exchange { _, response ->
                val status = response.statusCode
                if (status.is2xxSuccessful) {
                    response.bodyTo(OpenAIImageResponse::class.java)
                        ?: throw OpenAIException(status, "응답 본문이 비어 있다")
                } else {
                    // 본문을 먼저 문자열로 받는다. 곧바로 DTO 로 읽으면 파싱에 실패했을 때
                    // 스트림이 이미 소비돼 원문을 다시 볼 수 없다 — 그러면 "요청이 실패했다" 만 남아
                    // 무엇이 잘못됐는지 알 수 없다.
                    val raw = runCatching { response.bodyTo(String::class.java) }.getOrNull()
                    val parsed = raw?.let {
                        // 실패 본문은 {"error": {...}} 형태다. 아니면(HTML 오류 페이지 등) 원문을 그대로 남긴다.
                        runCatching { mapper.readValue(it, OpenAIErrorEnvelope::class.java).error?.message }.getOrNull()
                    }
                    throw OpenAIException(status, parsed ?: raw?.take(500)?.ifBlank { null } ?: "본문이 비어 있다")
                }
            }
    }
}

/**
 * OpenAI 호출 실패.
 *
 * 상태 코드를 그대로 들고 있는 이유는 **재시도 판단이 호출자의 몫**이기 때문이다.
 * 429·5xx 는 다시 시도할 만하고 4xx 는 아닌데, 그 정책은 전송이 정할 일이 아니다.
 */
class OpenAIException(
    val status: HttpStatusCode,
    /**
     * OpenAI 가 준 설명.
     *
     * [message] 와 따로 두는 이유는 `message` 에 상태 코드를 함께 담기 위해서다.
     * `message` 를 이 값으로 덮으면 상태 코드가 로그와 상위 예외에서 사라진다.
     */
    val detail: String,
) : RuntimeException("[$status] $detail") {

    /** 다시 시도해볼 만한 실패인가. 판단 재료일 뿐, 재시도 여부는 호출자가 정한다. */
    val retryable: Boolean
        get() = status.value() == 429 || status.is5xxServerError
}
