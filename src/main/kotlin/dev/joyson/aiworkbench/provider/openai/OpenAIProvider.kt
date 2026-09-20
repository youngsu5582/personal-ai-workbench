package dev.joyson.aiworkbench.provider.openai

import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResponse
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResult
import dev.joyson.aiworkbench.provider.ExternalApiProvider
import dev.joyson.aiworkbench.provider.ImageMetadata
import dev.joyson.aiworkbench.provider.ImageQuality
import dev.joyson.aiworkbench.provider.ProviderUsage
import java.util.Base64

/**
 * 표준 요청을 OpenAI 의 어휘로 옮기는 어댑터.
 *
 * 이 클래스가 하는 일은 **번역 네 가지**뿐이다.
 *   1. 모델 이름 → 세대와 경로 ([OpenAIImageModel])
 *   2. 표준 옵션(비율·해상도) → OpenAI 의 `size` 문자열
 *   3. OpenAI 실패 → [ExternalApiException] (재시도 가능 여부만 사실로 옮긴다)
 *   4. OpenAI 의 `usage` → [ProviderUsage] (해석하지 않고 그대로 싣는다)
 *
 * HTTP 는 [OpenAIImageClient] 가, 정책(재시도 횟수·다른 Provider 로 넘길지)은 호출하는 쪽이 맡는다.
 */
class OpenAIProvider(
    private val client: OpenAIImageClient,
) : ExternalApiProvider {

    override val name: String = PROVIDER_NAME

    /** 사실 질의다. 모르는 모델이면 예외가 아니라 false. */
    override fun supports(model: String): Boolean = OpenAIImageModel.find(model) != null

    override fun generate(
        model: String,
        request: ExternalApiGenerateRequest,
    ): ExternalApiGenerateResponse {
        // supports 를 건너뛰고 부를 수 있으므로 여기서도 확인한다. 다시 보내도 달라지지 않으니 재시도 대상이 아니다.
        val target = OpenAIImageModel.find(model)
            ?: throw ExternalApiException(retryable = false, message = "다루지 않는 모델이다: $model")

        val response = try {
            client.generate(
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
        } catch (e: OpenAIException) {
            // Provider 별 예외가 경계 밖으로 새지 않게 여기서 한 종류로 바꾼다.
            // 상태 코드를 메시지에 남긴다 — 어느 Provider 의 몇 번 실패였는지가 로그에서 사라지면
            // 재시도 판단은 되어도 원인 추적이 안 된다.
            throw ExternalApiException(
                retryable = e.retryable,
                message = "[$PROVIDER_NAME ${e.status}] ${e.detail}",
                cause = e,
            )
        }

        if (response.unknown.isNotEmpty) {
            // 지금은 버리지만 버렸다는 사실은 남긴다. 여기 뜨는 이름이 단가의 축이면
            // 그때 컬럼이나 jsonb 로 승격한다 — 온 줄도 모르는 것과는 다르다.
            // 객체를 그대로 넘긴다. 자르는 일은 toString 이 하므로 여기서 기억할 것이 없다.
            log.warn("OpenAI 가 우리가 모르는 필드를 보냈다. model={} 필드={}", model, response.unknown)
        }

        if (response.data.isEmpty()) {
            // 200 인데 이미지가 없는 건 설명되지 않는 상태다. 일시적일 수 있으니 재시도 대상으로 둔다.
            throw ExternalApiException(retryable = true, message = "응답에 이미지가 없다")
        }

        val decoder = Base64.getDecoder()
        return ExternalApiGenerateResponse(
            // OpenAI 는 토큰만 말하고 비용은 말하지 않는다. 달러로 바꾸는 것은 단가를 아는 쪽의 일이다.
            usage = response.usage?.let { ProviderUsage(raw = it) },
            result = response.data.map {
                val image = decoder.decode(it.b64Json)
                ExternalApiGenerateResult(
                    image = image,
                    metadata = ImageMetadata(
                        width = request.width,
                        height = request.height,
                        mimeType = "image/$OUTPUT_FORMAT",
                        fileSize = image.size,
                    ),
                    revisedPrompt = it.revisedPrompt,
                )
            },
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
