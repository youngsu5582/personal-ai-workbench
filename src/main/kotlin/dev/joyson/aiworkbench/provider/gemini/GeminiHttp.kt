package dev.joyson.aiworkbench.provider.gemini

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.http.HttpStatusCode
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper

/** 실패 본문을 원문으로 받아둔 뒤 파싱하기 위해 직접 읽는다. */
private val errorMapper = jacksonObjectMapper()

/**
 * 상태를 보고 응답을 읽거나 실패로 바꾼다.
 *
 * 본문을 먼저 문자열로 받는 이유는 OpenAI 쪽(`OpenAIHttp`)과 같다 — DTO 로 곧바로 읽다 실패하면 원문을 다시 볼 수 없다.
 */
internal fun <T : Any> RestClient.RequestHeadersSpec<*>.readOrThrow(type: Class<T>): T =
    exchange { _, response ->
        val status = response.statusCode
        if (status.is2xxSuccessful) {
            response.bodyTo(type) ?: throw GeminiException(status, "응답 본문이 비어 있다")
        } else {
            val raw = runCatching { response.bodyTo(String::class.java) }.getOrNull()
            val error = raw?.let {
                // 실패 본문은 {"error": {"code": 429, "message": …, "status": "RESOURCE_EXHAUSTED"}} 다.
                runCatching { errorMapper.readValue(it, GeminiErrorEnvelope::class.java).error }.getOrNull()
            }
            throw GeminiException(
                status = status,
                detail = error?.message ?: raw?.take(500)?.ifBlank { null } ?: "본문이 비어 있다",
                reason = error?.status,
            )
        }
    }

/**
 * Gemini 호출 실패. 상태 코드를 **해석하지 않고 그대로** 들고 있다 — 의미로 옮기는 일은 [GeminiFailures] 가 한다.
 */
class GeminiException(
    val status: HttpStatusCode,
    /** Gemini 가 준 설명. */
    val detail: String,
    /** Gemini 가 준 오류 이름(`error.status`, `RESOURCE_EXHAUSTED` 등). */
    val reason: String? = null,
) : RuntimeException("[$status${reason?.let { " $it" }.orEmpty()}] $detail")

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiErrorEnvelope(val error: GeminiError? = null)

/** `details` 에 재시도 시각 같은 것이 오지만 쓰지 않는다. 언제 다시 할지는 워커의 재시도 정책이 정한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
)
