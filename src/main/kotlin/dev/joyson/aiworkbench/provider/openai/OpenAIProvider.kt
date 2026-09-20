package dev.joyson.aiworkbench.provider.openai

import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResponse
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResult
import dev.joyson.aiworkbench.provider.ExternalApiImageInput
import dev.joyson.aiworkbench.provider.ExternalApiProvider
import dev.joyson.aiworkbench.provider.ImageMetadata
import dev.joyson.aiworkbench.provider.ImageQuality
import java.util.Base64

/**
 * 표준 요청을 OpenAI 의 어휘로 옮기는 어댑터.
 *
 * 이 클래스가 하는 일은 **번역 세 가지**뿐이다.
 *   1. 모델 이름 → 세대와 경로 ([OpenAIImageModel])
 *   2. 표준 옵션(비율·해상도) → OpenAI 의 `size` 문자열
 *   3. OpenAI 실패 → [ExternalApiException] (재시도 가능 여부만 사실로 옮긴다)
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

        rejectUnsendable(request.images)

        val size = "${request.width}x${request.height}"
        val quality = qualityOf(request.quality)

        val response = try {
            // **입력 이미지가 없으면 글에서 만들고, 있으면 그 이미지를 고친다.**
            // 종류를 가르는 것이 타입이 아니라 값이라(ExternalApiGenerateRequest.images)
            // 컴파일러가 이 분기를 지켜주지 못한다. 그래서 규칙을 포트와 여기 양쪽에 적어 둔다.
            if (request.images.isEmpty()) {
                client.generate(
                    path = OpenAIImageEndpoint.GENERATIONS.path,
                    request = OpenAIImageRequest(
                        model = target.modelName,
                        prompt = request.prompt,
                        // 호출 1건이 이미지 1장이다. 장수는 Task 개수로 나뉘어 들어온다.
                        n = 1,
                        size = size,
                        quality = quality,
                        outputFormat = OUTPUT_FORMAT,
                    ),
                )
            } else {
                client.edit(
                    path = OpenAIImageEndpoint.EDITS.path,
                    request = OpenAIImageEditRequest(
                        model = target.modelName,
                        prompt = request.prompt,
                        images = request.images.map { partOf(it) },
                        n = 1,
                        size = size,
                        quality = quality,
                        outputFormat = OUTPUT_FORMAT,
                    ),
                )
            }
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

        if (response.data.isEmpty()) {
            // 200 인데 이미지가 없는 건 설명되지 않는 상태다. 일시적일 수 있으니 재시도 대상으로 둔다.
            throw ExternalApiException(retryable = true, message = "응답에 이미지가 없다")
        }

        val decoder = Base64.getDecoder()
        return ExternalApiGenerateResponse(
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
     * 이 API 가 받지 않을 것이 확실한 요청을 보내기 전에 거른다.
     *
     * 무엇을 받는지 아는 곳은 어댑터뿐이라 여기서 한다. 왕복해서 400 을 받아올 이유가 없다 —
     * 정책을 정하는 것이 아니라 **실패라는 사실을 미리 옮기는 것**이다.
     *
     * 지금은 입력이 전부 우리가 만든 png 라 걸릴 일이 없다. 그래도 두는 이유는
     * 입력의 출처가 늘어나는 순간 이 검사가 첫 방어선이 되기 때문이다.
     */
    private fun rejectUnsendable(images: List<ExternalApiImageInput>) {
        if (images.size > MAX_INPUT_IMAGES) {
            throw ExternalApiException(
                retryable = false,
                message = "입력 이미지는 ${MAX_INPUT_IMAGES}장까지다: ${images.size}장",
            )
        }
        images.firstOrNull { it.mimeType !in SUPPORTED_INPUT_TYPES }?.let {
            throw ExternalApiException(retryable = false, message = "다루지 않는 입력 형식이다: ${it.mimeType}")
        }
    }

    /** 포트의 입력 이미지를 OpenAI 의 파트로 옮긴다. */
    private fun partOf(input: ExternalApiImageInput): OpenAIImageEditImage = OpenAIImageEditImage(
        bytes = input.bytes,
        contentType = input.mimeType,
        filename = input.filename,
    )

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

        /** 한 번에 실을 수 있는 참조 이미지 수. 문서 기준이며 미검증. */
        const val MAX_INPUT_IMAGES = 16

        /** 입력으로 받는 형식. 문서 기준이며 미검증. */
        val SUPPORTED_INPUT_TYPES = setOf("image/png", "image/jpeg", "image/webp")
    }
}
