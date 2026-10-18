package dev.joyson.aiworkbench.provider.openai

import org.springframework.http.HttpStatusCode
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper

/** 실패 본문을 원문으로 받아둔 뒤 파싱하기 위해 직접 읽는다. */
private val errorMapper = jacksonObjectMapper()

/**
 * 상태를 보고 응답을 읽거나 실패로 바꾼다.
 *
 * 이미지·텍스트 클라이언트가 함께 쓴다. API 가 갈리고 전송 형식이 갈려도(JSON·multipart)
 * **에러 규약은 하나여야** 하므로 여기 한 곳에 둔다. 두 벌이 되면 한쪽만 고쳐지고,
 * 그 차이는 장애가 났을 때에야 드러난다.
 */
internal fun <T : Any> RestClient.RequestHeadersSpec<*>.readOrThrow(type: Class<T>): T =
    exchange { _, response ->
        val status = response.statusCode
        if (status.is2xxSuccessful) {
            response.bodyTo(type) ?: throw OpenAIException(status, "응답 본문이 비어 있다")
        } else {
            // 본문을 먼저 문자열로 받는다. 곧바로 DTO 로 읽으면 파싱에 실패했을 때
            // 스트림이 이미 소비돼 원문을 다시 볼 수 없다 — 그러면 "요청이 실패했다" 만 남아
            // 무엇이 잘못됐는지 알 수 없다.
            val raw = runCatching { response.bodyTo(String::class.java) }.getOrNull()
            val error = raw?.let {
                // 실패 본문은 {"error": {...}} 형태다. 아니면(HTML 오류 페이지 등) 원문을 그대로 남긴다.
                runCatching { errorMapper.readValue(it, OpenAIErrorEnvelope::class.java).error }.getOrNull()
            }
            throw OpenAIException(
                status = status,
                detail = error?.message ?: raw?.take(500)?.ifBlank { null } ?: "본문이 비어 있다",
                code = error?.code,
            )
        }
    }

/**
 * OpenAI 호출 실패.
 *
 * 상태 코드를 **해석하지 않고 그대로** 들고 있다. 429 와 5xx 를 가르는 일은
 * `FailureKind` 로 옮기는 어댑터가 한 곳에서 한다([OpenAIFailures]) — 전송이 같은 판단을 따로 내리면
 * 둘이 어긋날 수 있고, 어긋나도 알아차릴 방법이 없다.
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
    /** OpenAI 가 준 오류 이름(`error.code`). 주지 않는 오류가 많다. */
    val code: String? = null,
) : RuntimeException("[$status${code?.let { " $it" }.orEmpty()}] $detail")
