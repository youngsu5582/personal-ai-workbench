package dev.joyson.aiworkbench.provider.openai

import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper

/** 실패 본문을 원문으로 받아둔 뒤 파싱하기 위해 직접 읽는다. */
private val errorMapper = jacksonObjectMapper()

/**
 * 상태를 보고 응답을 읽거나 실패로 바꾼다.
 *
 * API 와 전송 형식이 갈려도(Images·Decisions, JSON·multipart) **에러 규약은 하나여야** 하므로 여기 한 곳에 둔다.
 * 두 벌이 되면 한쪽만 고쳐지고, 그 차이는 장애가 났을 때에야 드러난다.
 */
internal fun <T : Any> exchange(spec: RestClient.RequestHeadersSpec<*>, type: Class<T>): T =
    spec.exchange { _, response ->
        val status = response.statusCode
        if (status.is2xxSuccessful) {
            response.bodyTo(type)
                ?: throw OpenAIException(status, "응답 본문이 비어 있다")
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
