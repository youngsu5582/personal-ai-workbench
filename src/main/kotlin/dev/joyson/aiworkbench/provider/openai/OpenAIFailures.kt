package dev.joyson.aiworkbench.provider.openai

import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.FailureKind
import org.slf4j.Logger
import org.springframework.web.client.ResourceAccessException

/**
 * OpenAI 쪽의 **모든** 실패를 [ExternalApiException] 으로 옮긴다. 이미지·텍스트 어댑터가 함께 쓴다.
 *
 * 포트는 실패를 [ExternalApiException] 으로 준다고 약속했다. 어댑터가 둘이면 그 약속도 두 번 지켜야 하는데,
 * 한쪽에서만 고친 번역은 같은 429 를 다른 종류로 적게 만든다 — 그러면 과금 판정이 Provider 가 아니라
 * 작업 종류에 따라 갈린다. 그래서 번역표를 한 곳에 둔다.
 */
internal object OpenAIFailures {

    /**
     * [call] 을 돌리고, 새는 예외를 전부 포트의 실패로 바꾼다.
     *
     * 호출만이 아니라 **응답을 우리 타입으로 옮기는 일까지** 감싸야 한다. 거기서 나는 실패는
     * 호출이 이미 성공한 뒤라 돈은 나갔는데, 부르는 쪽이 못 잡으면 Task 가 RUNNING 에 영원히 남고
     * 그 지출도 기록되지 않는다. 무엇이 샐지 미리 알 수 없으므로 자리로도 종류로도 가리지 않는다.
     */
    fun <T> translate(log: Logger, model: String, call: () -> T): T = try {
        call()
    } catch (e: ExternalApiException) {
        // 어댑터가 **사실로 판단해** 던진 것이다. 다시 감싸면 정해 둔 종류가 덮이고
        // 사유가 한 겹 묻힌다. 그대로 올린다.
        throw e
    } catch (e: OpenAIException) {
        // Provider 별 예외가 경계 밖으로 새지 않게 여기서 한 종류로 바꾼다.
        // 상태 코드를 메시지에 남긴다 — 어느 Provider 의 몇 번 실패였는지가 로그에서 사라지면
        // 재시도 판단은 되어도 원인 추적이 안 된다.
        throw ExternalApiException(
            kind = kindOf(e),
            message = "[$PROVIDER_NAME ${e.status}${e.code?.let { " $it" }.orEmpty()}] ${e.detail}",
            cause = e,
        )
    } catch (e: ResourceAccessException) {
        // 응답을 받지 못한 실패다 — 타임아웃·연결 끊김. 상태 코드가 없어 위에서 걸리지 않는다.
        //
        // 다시 해볼 만하다고 본다. 다만 **타임아웃이면 저쪽은 이미 만들어 과금했을 수 있어**
        // 재시도가 두 번 내는 것이 될 수 있다. 그래도 포기하면 낸 돈만큼을 못 받고,
        // 시도 상한이 막아주며, 시도마다 기록이 남아 그 낭비가 눈에 보인다.
        throw ExternalApiException(
            kind = FailureKind.NO_RESPONSE,
            message = "[$PROVIDER_NAME 응답 없음] ${e.message}",
            cause = e,
        )
    } catch (e: Exception) {
        // 원인을 모르니 재시도하지 않는다 — 모르는 채로 같은 호출을 세 번 하면 돈만 세 번 나간다.
        // 스택트레이스는 여기서만 남길 수 있다. 밖으로 나가면 사유 문자열 255자로 잘린다.
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
     * 과금 판정이 여기서 갈린다. 429 와 5xx 는 둘 다 "다시 해볼 만하다" 지만
     * 전자는 아무것도 만들지 않았고 후자는 저쪽에서 뭔가 하다 실패한 것이라, 한 종류로 묶으면
     * 나중에 "돈이 나갔나" 를 이 값으로 답할 수 없다.
     *
     * 입력 주소를 받아가지 못한 4xx 는 따로 가른다. 바이트로 다시 보내면 될 수 있어서다.
     * 그때 오는 `code` 는 `invalid_value` 로, 다른 잘못된 값에도 쓰이는 이름이라 이것만으로는 가를 수 없다. 그래서 문구로 가른다.
     * 문구가 바뀌면 [FailureKind.REJECTED] 로 떨어진다 — 다시 보내지 않을 뿐 틀린 판정은 아니다.
     */
    fun kindOf(e: OpenAIException): FailureKind = when {
        e.status.value() == 429 -> FailureKind.THROTTLED
        e.status.is5xxServerError -> FailureKind.PROVIDER_ERROR
        e.status.is4xxClientError && e.detail.startsWith(INPUT_DOWNLOAD_FAILED) -> FailureKind.INPUT_UNREACHABLE
        e.status.is4xxClientError -> FailureKind.REJECTED
        // 2xx·3xx 로 여기 오는 건 설명되지 않는다. 모르는 채로 재시도하지 않는다.
        else -> FailureKind.UNKNOWN
    }

    const val PROVIDER_NAME = "openai"

    /** 주소로 넘긴 입력 이미지를 OpenAI 가 받아오지 못했을 때 오는 문구의 앞부분. */
    private const val INPUT_DOWNLOAD_FAILED = "Error while downloading file"
}
