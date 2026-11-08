package dev.joyson.aiworkbench.provider.gemini

import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.FailureKind
import org.slf4j.Logger
import org.springframework.web.client.ResourceAccessException

/**
 * Gemini 쪽의 **모든** 실패를 [ExternalApiException] 으로 옮긴다.
 *
 * 갈래와 이유는 OpenAI 쪽([dev.joyson.aiworkbench.provider.openai.OpenAIFailures])과 같다. 하나로 합치지 않은 이유는
 * 오류 본문의 모양과 오류 이름의 체계가 Provider 마다 달라서다. 같은 부분(연결 실패 · 모르는 예외)은 Provider 가 셋이 될 때 합친다.
 */
internal object GeminiFailures {

    /** [call] 을 돌리고, 새는 예외를 전부 포트의 실패로 바꾼다. 응답을 우리 타입으로 옮기는 일까지 감싼다. */
    fun <T> translate(log: Logger, model: String, call: () -> T): T = try {
        call()
    } catch (e: ExternalApiException) {
        // 어댑터가 사실로 판단해 던진 것이다. 다시 감싸면 정해 둔 종류가 덮인다.
        throw e
    } catch (e: GeminiException) {
        throw ExternalApiException(
            kind = kindOf(e),
            message = "[$PROVIDER_NAME ${e.status}${e.reason?.let { " $it" }.orEmpty()}] ${e.detail}",
            cause = e,
        )
    } catch (e: ResourceAccessException) {
        // 응답을 받지 못했다. 타임아웃이면 저쪽은 이미 만들어 과금했을 수 있다 — 판단은 OpenAI 쪽과 같다.
        throw ExternalApiException(
            kind = FailureKind.NO_RESPONSE,
            message = "[$PROVIDER_NAME 응답 없음] ${e.message}",
            cause = e,
        )
    } catch (e: Exception) {
        // 원인을 모르니 재시도하지 않는다. 스택트레이스는 여기서만 남길 수 있다.
        log.error("설명되지 않는 오류다. model={}", model, e)
        throw ExternalApiException(
            kind = FailureKind.UNKNOWN,
            message = "[$PROVIDER_NAME 알 수 없는 오류] ${e.message}",
            cause = e,
        )
    }

    /**
     * 상태 코드를 의미로 옮긴다.
     *
     * Free Tier 의 일일 한도도 429(`RESOURCE_EXHAUSTED`)로 온다. 분당 한도와 가르지 않는다 —
     * 다시 해서 될지는 기다린 시간에 달렸고, 재시도 상한이 막아 준다. 끝내 실패하면 사유가 Task 에 남는다.
     */
    fun kindOf(e: GeminiException): FailureKind = when {
        e.status.value() == 429 -> FailureKind.THROTTLED
        e.status.is5xxServerError -> FailureKind.PROVIDER_ERROR
        e.status.is4xxClientError -> FailureKind.REJECTED
        else -> FailureKind.UNKNOWN
    }

    const val PROVIDER_NAME = "gemini"
}
