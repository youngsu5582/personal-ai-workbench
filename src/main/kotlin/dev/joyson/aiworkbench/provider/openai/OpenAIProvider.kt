package dev.joyson.aiworkbench.provider.openai

import dev.joyson.aiworkbench.provider.AppliedParameters
import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResponse
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResult
import dev.joyson.aiworkbench.provider.ExternalApiProvider
import dev.joyson.aiworkbench.provider.FailureKind
import dev.joyson.aiworkbench.provider.ImageMetadata
import dev.joyson.aiworkbench.provider.ImageQuality
import dev.joyson.aiworkbench.provider.ProviderUsage
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.web.client.ResourceAccessException
import java.util.Base64

/**
 * 표준 요청을 OpenAI 의 어휘로 옮기는 어댑터.
 *
 * 이 클래스가 하는 일은 **번역 네 가지**뿐이다.
 *   1. 모델 이름 → 세대와 경로 ([OpenAIImageModel])
 *   2. 표준 옵션(비율·해상도) → OpenAI 의 `size` 문자열
 *   3. **모든** 실패 → [ExternalApiException] (재시도 가능 여부만 사실로 옮긴다)
 *   4. OpenAI 의 `usage` → [ProviderUsage] (해석하지 않고 그대로 싣는다)
 *
 * HTTP 는 [OpenAIImageClient] 가, 정책(재시도 횟수·다른 Provider 로 넘길지)은 호출하는 쪽이 맡는다.
 */
class OpenAIProvider(
    private val client: OpenAIImageClient,
) : ExternalApiProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override val name: String = PROVIDER_NAME

    /** 사실 질의다. 모르는 모델이면 예외가 아니라 false. */
    override fun supports(model: String): Boolean = OpenAIImageModel.find(model) != null

    override fun generate(
        model: String,
        request: ExternalApiGenerateRequest,
    ): ExternalApiGenerateResponse = try {
        // supports 를 건너뛰고 부를 수 있으므로 여기서도 확인한다. 다시 보내도 달라지지 않으니 재시도 대상이 아니다.
        val target = OpenAIImageModel.find(model)
            ?: throw ExternalApiException(FailureKind.REJECTED, "다루지 않는 모델이다: $model")

        val response = client.generate(
            path = OpenAIImageEndpoint.GENERATIONS.path,
            request = OpenAIImageRequest(
                model = target.modelName,
                prompt = request.prompt,
                // 호출 1건이 이미지 1장이다. 장수는 Task 개수로 나뉘어 들어온다.
                n = 1,
                size = "${request.width}x${request.height}",
                quality = qualityOf(request.quality),
                outputFormat = OUTPUT_FORMAT,
            ),
        )

        if (response.unknown.isNotEmpty) {
            // 지금은 버리지만 버렸다는 사실은 남긴다. 여기 뜨는 이름이 단가의 축이면
            // 그때 컬럼이나 jsonb 로 승격한다 — 온 줄도 모르는 것과는 다르다.
            // 객체를 그대로 넘긴다. 자르는 일은 toString 이 하므로 여기서 기억할 것이 없다.
            log.warn("OpenAI 가 우리가 모르는 필드를 보냈다. model={} 필드={}", model, response.unknown)
        }

        val unknownInImages = response.data.map { it.unknown }.filter { it.isNotEmpty }
        if (unknownInImages.isNotEmpty()) {
            // 최상위 갈고리는 결과물 안을 못 본다. 거기서 버리는 것도 이름은 남겨야 한다.
            // 리스트를 그대로 넘긴다 — 원소마다 toString 이 값을 잘라서 낸다.
            log.warn("OpenAI 가 결과물에 우리가 모르는 필드를 보냈다. model={} 필드={}", model, unknownInImages)
        }

        if (response.data.isEmpty()) {
            // 200 인데 이미지가 없는 건 설명되지 않는 상태다. 일시적일 수 있으니 재시도 대상으로 둔다.
            // 저쪽이 설명되지 않는 응답을 준 것이라 다시 받으면 멀쩡할 수 있다.
            throw ExternalApiException(FailureKind.PROVIDER_ERROR, "응답에 이미지가 없다")
        }

        assemble(request, response)
    } catch (e: ExternalApiException) {
        // 우리가 **사실로 판단해** 던진 것이다. 다시 감싸면 우리가 정한 재시도 여부가 덮이고
        // 사유가 한 겹 묻힌다. 그대로 올린다.
        throw e
    } catch (e: OpenAIException) {
        // Provider 별 예외가 경계 밖으로 새지 않게 여기서 한 종류로 바꾼다.
        // 상태 코드를 메시지에 남긴다 — 어느 Provider 의 몇 번 실패였는지가 로그에서 사라지면
        // 재시도 판단은 되어도 원인 추적이 안 된다.
        throw ExternalApiException(
            kind = kindOf(e.status),
            message = "[$PROVIDER_NAME ${e.status}] ${e.detail}",
            cause = e,
        )
    } catch (e: ResourceAccessException) {
        // 응답을 받지 못한 실패다 — 타임아웃·연결 끊김. 상태 코드가 없어 위에서 걸리지 않는다.
        //
        // 다시 해볼 만하다고 본다. 다만 **타임아웃이면 저쪽은 이미 만들어 과금했을 수 있어**
        // 재시도가 두 번 내는 것이 될 수 있다. 그래도 포기하면 낸 돈만큼을 못 받고,
        // 시도 상한이 막아주며, 이제는 시도마다 기록이 남아 그 낭비가 눈에 보인다.
        throw ExternalApiException(
            kind = FailureKind.NO_RESPONSE,
            message = "[$PROVIDER_NAME 응답 없음] ${e.message}",
            cause = e,
        )
    } catch (e: Exception) {
        // 포트는 실패를 [ExternalApiException] 으로 준다고 약속했다. 그 약속을 여기서 **구조적으로** 지킨다.
        //
        // try 가 메서드 전체를 덮는 이유: 호출만 감싸면 응답을 우리 타입으로 옮기는 동안 나는 실패가
        // 그대로 샌다. 거기는 **호출이 이미 성공한 뒤**라 돈은 나갔는데 부르는 쪽이 못 잡아
        // Task 가 RUNNING 에 영원히 남고 그 지출도 기록되지 않는다.
        // 무엇이 샐지 미리 알 수 없으므로 자리로도 종류로도 가리지 않는다.
        //
        // 원인을 모르니 재시도하지 않는다 — 모르는 채로 같은 호출을 세 번 하면 돈만 세 번 나간다.
        // 스택트레이스는 여기서만 남길 수 있다. 밖으로 나가면 사유 문자열 255자로 잘린다.
        log.error("설명되지 않는 오류다. model={}", model, e)
        throw ExternalApiException(
            kind = FailureKind.UNKNOWN,
            message = "[$PROVIDER_NAME 알 수 없는 오류] ${e.message}",
            cause = e,
        )
    }

    /** 응답을 포트 타입으로 옮긴다. 여기서 나는 실패는 부르는 쪽이 [ExternalApiException] 으로 받는다. */
    private fun assemble(
        request: ExternalApiGenerateRequest,
        response: OpenAIImageResponse,
    ): ExternalApiGenerateResponse {
        val applied = appliedOf(request, response)
        val decoder = Base64.getDecoder()
        return ExternalApiGenerateResponse(
            // OpenAI 는 토큰만 말하고 비용은 말하지 않는다. 달러로 바꾸는 것은 단가를 아는 쪽의 일이다.
            usage = response.usage?.let { ProviderUsage(raw = it) },
            applied = applied,
            result = response.data.map {
                val image = decoder.decode(it.b64Json)
                ExternalApiGenerateResult(
                    image = image,
                    metadata = ImageMetadata(
                        // 우리가 보낸 값이 아니라 저쪽이 실제로 만든 값이다.
                        width = applied.width,
                        height = applied.height,
                        mimeType = "image/${applied.outputFormat ?: OUTPUT_FORMAT}",
                        fileSize = image.size,
                    ),
                    revisedPrompt = it.revisedPrompt,
                    providerId = it.generationId,
                )
            },
        )
    }

    /**
     * 상태 코드를 의미로 옮긴다.
     *
     * 과금 판정이 여기서 갈린다. 429 와 5xx 는 둘 다 "다시 해볼 만하다" 지만
     * 전자는 아무것도 만들지 않았고 후자는 저쪽에서 뭔가 하다 실패한 것이라, 한 종류로 묶으면
     * 나중에 "돈이 나갔나" 를 이 값으로 답할 수 없다.
     */
    private fun kindOf(status: HttpStatusCode): FailureKind = when {
        status.value() == 429 -> FailureKind.THROTTLED
        status.is5xxServerError -> FailureKind.PROVIDER_ERROR
        status.is4xxClientError -> FailureKind.REJECTED
        // 2xx·3xx 로 여기 오는 건 설명되지 않는다. 모르는 채로 재시도하지 않는다.
        else -> FailureKind.UNKNOWN
    }

    /**
     * `1024x1024` 를 픽셀 둘로 푼다. 모양이 다르면 null — 못 읽는 것은 예외가 아니라 모르는 것이다.
     *
     * 저쪽이 언제든 표기를 바꿀 수 있어서, 못 읽었다고 호출을 실패시키지 않는다.
     * 그때는 우리가 보낸 값으로 물러선다.
     */
    private fun pixelsOf(size: String?): Pair<Int, Int>? {
        val parts = size?.split("x")?.takeIf { it.size == 2 } ?: return null
        val width = parts[0].trim().toIntOrNull() ?: return null
        val height = parts[1].trim().toIntOrNull() ?: return null
        return if (width > 0 && height > 0) width to height else null
    }

    /**
     * 응답이 말한 실제 값을 모은다. 안 말한 것은 보낸 값으로 채운다.
     *
     * 크기가 다르면 경고를 남긴다. **조용히 다른 크기를 주는 것이 제일 나쁘다** —
     * 과금은 저쪽이 만든 것에 붙는데 우리 기록은 요청한 것을 말하고 있으면 둘이 어긋난다.
     */
    private fun appliedOf(
        request: ExternalApiGenerateRequest,
        response: OpenAIImageResponse,
    ): AppliedParameters {
        val pixels = pixelsOf(response.size)
        if (pixels != null && (pixels.first != request.width || pixels.second != request.height)) {
            log.warn(
                "요청한 크기와 만들어진 크기가 다르다. 요청={}x{} 실제={}x{}",
                request.width, request.height, pixels.first, pixels.second,
            )
        }

        return AppliedParameters(
            width = pixels?.first ?: request.width,
            height = pixels?.second ?: request.height,
            quality = response.quality ?: qualityOf(request.quality),
            background = response.background,
            // 우리가 보내는 값이라, 저쪽이 안 답해도 무엇으로 만들어졌는지는 안다.
            outputFormat = response.outputFormat ?: OUTPUT_FORMAT,
        )
    }

    /**
     * 포트의 품질 눈금을 OpenAI 의 허용값으로 옮긴다.
     *
     * 지금은 값이 1:1 로 대응한다. 다른 Provider 가 다른 눈금을 쓰면 그 어댑터가 자기 표를 갖는다.
     */
    private fun qualityOf(quality: ImageQuality): String = when (quality) {
        ImageQuality.LOW -> "low"
        ImageQuality.MEDIUM -> "medium"
        ImageQuality.HIGH -> "high"
        ImageQuality.XHIGH -> "xhigh"
        ImageQuality.MAX -> "max"
        ImageQuality.AUTO -> "auto"
    }

    companion object {
        const val PROVIDER_NAME = "openai"

        /** png 로 고정한다. 투명 배경을 지원하는 유일한 형식이고, 저장 포맷 선택은 아직 제품 기능이 아니다. */
        const val OUTPUT_FORMAT = "png"
    }
}
