package dev.joyson.aiworkbench.provider.openai

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * OpenAI Decisions API(`/v1/decisions`) 의 요청·응답을 그대로 옮긴 타입이다.
 *
 * 이 파일은 **우리 도메인을 모른다.** 텍스트 DTO 와 같은 자리다 — 옮기는 일은 어댑터가 한다.
 *
 * **미검증.** 필드 이름과 모양은 실제 API 로 확인하지 않았다. 확인하는 자리는 `OpenAIDecisionProviderLiveTest` 다.
 */
data class OpenAIDecisionRequest(
    val model: String,
    /** 근거는 user 메시지 하나에 순서대로 싣는다. */
    val input: List<OpenAIDecisionMessage>,
    val questions: List<OpenAIDecisionQuestion>,
)

data class OpenAIDecisionMessage(
    val content: List<OpenAIDecisionContent>,
    val role: String = "user",
)

/** 근거 한 조각. [type] 이 판별자다. */
sealed interface OpenAIDecisionContent {
    val type: String

    data class Text(val text: String) : OpenAIDecisionContent {
        override val type = "input_text"
    }

    /** 주소 또는 `data:` URL. */
    data class Image(
        @param:JsonProperty("image_url")
        @get:JsonProperty("image_url")
        val imageUrl: String,
    ) : OpenAIDecisionContent {
        override val type = "input_image"
    }
}

/** 질문 하나. [type] 이 판별자다. */
sealed interface OpenAIDecisionQuestion {
    val type: String
    val name: String
    val instructions: String

    data class Predicate(override val name: String, override val instructions: String) : OpenAIDecisionQuestion {
        override val type = "predicate"
    }

    data class Choice(
        override val name: String,
        override val instructions: String,
        val choices: List<OpenAIDecisionChoice>,
    ) : OpenAIDecisionQuestion {
        override val type = "choice"
    }

    data class Score(
        override val name: String,
        override val instructions: String,
        val levels: List<OpenAIDecisionLevel>,
    ) : OpenAIDecisionQuestion {
        override val type = "score"
    }
}

data class OpenAIDecisionChoice(val value: String, val description: String)

data class OpenAIDecisionLevel(val label: String, val description: String)

/**
 * 응답. 텍스트 응답처럼 모르는 필드를 모으지 않는다 — 단가의 축은 [usage] 에 원문 그대로 남는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAIDecisionResponse(
    val id: String? = null,
    val answers: List<OpenAIDecisionAnswer> = emptyList(),
    /** 텍스트 응답과 같은 이유로 **Map 으로 통째로** 받는다. 과금 근거라 항목이 말없이 사라지면 안 된다. */
    val usage: Map<String, Any?>? = null,
)

/** 답 하나. 어떤 값이 오는지는 [type] 이 정한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAIDecisionAnswer(
    /** `predicate` · `choice` · `score` · `refusal` */
    val type: String,
    val name: String? = null,
    val probability: Double? = null,
    val choice: String? = null,
    val score: Double? = null,
    val confidence: Double? = null,
)
