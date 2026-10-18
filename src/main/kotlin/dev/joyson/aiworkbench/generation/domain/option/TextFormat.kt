package dev.joyson.aiworkbench.generation.domain.option

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * 받을 글의 모양.
 *
 * 판별자는 **이 값이 저장되기 때문에** 둔다. 판별자 이름이 곧 저장된 행의 계약이다.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = TextFormat.Text::class, name = "text"),
    JsonSubTypes.Type(value = TextFormat.JsonSchema::class, name = "json-schema"),
)
sealed interface TextFormat {

    /**
     * 모델이 낸 글이 이 모양인가.
     *
     * 스키마와 하나하나 대조하지는 않는다 — 그건 Provider 가 strict 로 지킨다. 여기서 보는 것은
     * 그 약속이 깨졌을 때 받는 쪽이 파싱하다 터지지 않을 만큼이다.
     */
    fun conforms(text: String): Boolean

    /** 모양을 정하지 않은 글. */
    data object Text : TextFormat {
        override fun conforms(text: String): Boolean = true
    }

    /**
     * 이 JSON Schema 를 따르는 JSON 객체.
     *
     * 스키마를 객체가 아니라 **원문 문자열로** 든다. 출력 키가 스키마의 키 순서대로 나오는데
     * (OpenAI Structured Outputs), 이 값이 저장되는 칸(`generation_jobs.option`)은 jsonb 라
     * 키를 제 기준으로 다시 늘어놓는다. 객체로 두면 `reasoning` 다음 `answer` 로 짠 스키마가
     * 저장을 한 번 거친 뒤 `answer` 부터 답하게 된다. 문자열은 jsonb 안에서도 글자 그대로 남는다.
     *
     * 그래서 요청에서는 객체로 받고, 저장된 행과 조회 응답의 `option` 에서는 문자열로 보인다.
     */
    data class JsonSchema(val schema: String) : TextFormat {
        init {
            require(parse(schema)?.isObject == true) { "schema 는 JSON 객체여야 한다" }
        }

        override fun conforms(text: String): Boolean = parse(text)?.isObject == true

        companion object {
            /**
             * 요청은 객체로, 저장된 행은 문자열로 온다. 둘 다 받는다.
             *
             * 객체를 문자열로 펴도 순서가 지켜진다 — 파서가 읽은 순서대로 담고 그 순서대로 쓴다.
             */
            @JvmStatic
            @JsonCreator
            fun of(@JsonProperty("schema") schema: JsonNode): JsonSchema =
                JsonSchema(if (schema.isString) schema.stringValue() else schema.toString())

            private fun parse(json: String): JsonNode? =
                try {
                    JsonMapper.shared().readTree(json)
                } catch (_: JacksonException) {
                    null
                }
        }
    }
}
