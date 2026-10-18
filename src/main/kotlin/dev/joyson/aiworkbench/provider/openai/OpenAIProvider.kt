package dev.joyson.aiworkbench.provider.openai

import dev.joyson.aiworkbench.provider.AppliedParameters
import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResponse
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResult
import dev.joyson.aiworkbench.provider.ExternalApiFileInput
import dev.joyson.aiworkbench.provider.ExternalApiProvider
import dev.joyson.aiworkbench.provider.FailureKind
import dev.joyson.aiworkbench.provider.ImageMetadata
import dev.joyson.aiworkbench.provider.ImageQuality
import dev.joyson.aiworkbench.provider.ProviderUsage
import dev.joyson.aiworkbench.provider.UnknownFields
import org.slf4j.LoggerFactory
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * 표준 요청을 OpenAI 의 어휘로 옮기는 어댑터.
 *
 * 이 클래스가 하는 일은 **번역 네 가지**뿐이다.
 *   1. 모델 이름 → 세대와 경로 ([OpenAIImageModel])
 *   2. 픽셀 크기 → OpenAI 의 `size` 표기 ([OpenAISize])
 *   3. **모든** 실패 → [ExternalApiException] (번역표는 텍스트 어댑터와 함께 쓰는 [OpenAIFailures] 에 있다)
 *   4. OpenAI 의 `usage` → [ProviderUsage] (해석하지 않고 그대로 싣는다)
 *
 * HTTP 는 [OpenAIImageClient] 가, 정책(재시도 횟수·다른 Provider 로 넘길지)은 호출하는 쪽이 맡는다.
 */
class OpenAIProvider(
    private val client: OpenAIImageClient,
) : ExternalApiProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override val name: String = OpenAIFailures.PROVIDER_NAME

    /** 사실 질의다. 모르는 모델이면 예외가 아니라 false. */
    override fun supports(model: String): Boolean = OpenAIImageModel.find(model) != null

    override fun generate(
        model: String,
        request: ExternalApiGenerateRequest,
    ): ExternalApiGenerateResponse = OpenAIFailures.translate(log, model) {
        // supports 를 건너뛰고 부를 수 있으므로 여기서도 확인한다. 다시 보내도 달라지지 않으니 재시도 대상이 아니다.
        val target = OpenAIImageModel.find(model)
            ?: throw ExternalApiException(FailureKind.REJECTED, "다루지 않는 모델이다: $model")

        rejectUnsendable(request.images)

        val size = OpenAISize(request.width, request.height).notation
        val quality = qualityOf(request.quality)

        // **입력 이미지가 없으면 글에서 만들고, 있으면 그 이미지를 고친다.**
        // 종류를 가르는 것이 타입이 아니라 값이라(ExternalApiGenerateRequest.images)
        // 컴파일러가 이 분기를 지켜주지 못한다. 그래서 규칙을 포트와 여기 양쪽에 적어 둔다.
        val response = if (request.images.isEmpty()) {
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

        // 지금은 버리지만 버렸다는 사실은 남긴다. 여기 뜨는 이름이 단가의 축이면
        // 그때 컬럼이나 jsonb 로 승격한다 — 온 줄도 모르는 것과는 다르다.
        warnUnknownOnce(model, "응답에", response.unknown)
        // 최상위 갈고리는 결과물 안을 못 본다. 거기서 버리는 것도 이름은 남겨야 한다.
        response.data.forEach { warnUnknownOnce(model, "결과물에", it.unknown) }

        if (response.data.isEmpty()) {
            // 200 인데 이미지가 없는 건 설명되지 않는 상태다. 일시적일 수 있으니 재시도 대상으로 둔다.
            // 저쪽이 설명되지 않는 응답을 준 것이라 다시 받으면 멀쩡할 수 있다.
            throw ExternalApiException(FailureKind.PROVIDER_ERROR, "응답에 이미지가 없다")
        }

        assemble(request, response)
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
     * 이미 알린 미지 필드. `위치:이름` 으로 센다 — 승격할 때 그 필드를 둘 DTO 가 위치로 갈린다.
     *
     * 미지 필드는 **사건이 아니라 상태**다. 저쪽이 필드를 하나 늘리면 우리가 그것을 선언할 때까지
     * 모든 응답에 들어 있어서, 매번 찍으면 100% 의 호출이 같은 줄을 낸다. 로그가 덮이는 것보다
     * 나쁜 것은 사람이 이 WARN 을 무시하게 되는 것이다 — 그러면 다음에 진짜 새 이름이 와도 안 보인다.
     *
     * 프로세스마다 다시 센다. 뜰 때 한 번 더 알리는 것은 낭비가 아니라 노린 것이다 —
     * 배포 직후가 저쪽 응답이 달라졌는지 볼 자리다. 그래서 프로세스 밖에 두지 않는다.
     */
    private val reportedUnknowns = ConcurrentHashMap.newKeySet<String>()

    /**
     * 처음 보는 이름이 있을 때만 남긴다.
     *
     * 값은 함께 낸다 — 승격할지 정하려면 모양을 봐야 한다. 자르는 일은 `UnknownFields.toString` 이
     * 하므로 여기서 기억할 것이 없다.
     */
    private fun warnUnknownOnce(model: String, where: String, fields: UnknownFields) {
        val fresh = fields.names.filterNot { "$where:$it" in reportedUnknowns }
        if (fresh.isEmpty()) return

        fresh.forEach { reportedUnknowns.add("$where:$it") }
        log.warn("OpenAI 가 {} 우리가 모르는 필드를 보냈다. model={} 처음 본 이름={} 값={}", where, model, fresh, fields)
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
        val sent = OpenAISize(request.width, request.height)
        val made = OpenAISize.parseOrNull(response.size)
        if (made != null && made != sent) {
            log.warn("요청한 크기와 만들어진 크기가 다르다. 요청={} 실제={}", sent.notation, made.notation)
        }

        // 못 읽었으면 보낸 값으로 물러선다.
        val size = made ?: sent
        return AppliedParameters(
            width = size.width,
            height = size.height,
            quality = response.quality ?: qualityOf(request.quality),
            background = response.background,
            // 우리가 보내는 값이라, 저쪽이 안 답해도 무엇으로 만들어졌는지는 안다.
            outputFormat = response.outputFormat ?: OUTPUT_FORMAT,
        )
    }

    /**
     * 이 API 가 받지 않을 것이 확실한 요청을 보내기 전에 거른다.
     *
     * 무엇을 받는지 아는 곳은 어댑터뿐이라 여기서 한다. 왕복해서 400 을 받아올 이유가 없다 —
     * 정책을 정하는 것이 아니라 **실패라는 사실을 미리 옮기는 것**이다.
     * 보내지 않았으니 과금도 없다. 그래서 [FailureKind.REJECTED] 다.
     *
     * 지금은 입력이 전부 우리가 만든 png 라 걸릴 일이 없다. 그래도 두는 이유는
     * 입력의 출처가 늘어나는 순간 이 검사가 첫 방어선이 되기 때문이다.
     */
    private fun rejectUnsendable(images: List<ExternalApiFileInput>) {
        if (images.size > MAX_INPUT_IMAGES) {
            throw ExternalApiException(
                kind = FailureKind.REJECTED,
                message = "입력 이미지는 ${MAX_INPUT_IMAGES}장까지다: ${images.size}장",
            )
        }
        images.firstOrNull { it.mimeType !in SUPPORTED_INPUT_TYPES }?.let {
            throw ExternalApiException(
                kind = FailureKind.REJECTED,
                message = "다루지 않는 입력 형식이다: ${it.mimeType}",
            )
        }
    }

    /** 포트의 입력 이미지를 OpenAI 의 파트로 옮긴다. */
    private fun partOf(input: ExternalApiFileInput): OpenAIImageEditImage = when (input) {
        is ExternalApiFileInput.Bytes -> OpenAIImageEditImage.Bytes(input.bytes, input.mimeType, input.filename)
        is ExternalApiFileInput.Url -> OpenAIImageEditImage.Url(input.url)
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
        /** png 로 고정한다. 투명 배경을 지원하는 유일한 형식이고, 저장 포맷 선택은 아직 제품 기능이 아니다. */
        const val OUTPUT_FORMAT = "png"

        /** 한 번에 실을 수 있는 참조 이미지 수. 문서 기준이며 미검증. */
        const val MAX_INPUT_IMAGES = 16

        /** 입력으로 받는 형식. 문서 기준이며 미검증. */
        val SUPPORTED_INPUT_TYPES = setOf("image/png", "image/jpeg", "image/webp")
    }
}
