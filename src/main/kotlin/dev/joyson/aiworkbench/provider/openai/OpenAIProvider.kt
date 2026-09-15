package dev.joyson.aiworkbench.provider.openai

import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResponse
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResult
import dev.joyson.aiworkbench.provider.ExternalApiProvider
import dev.joyson.aiworkbench.provider.ImageMetadata
import dev.joyson.aiworkbench.provider.domain.ImageSize
import dev.joyson.aiworkbench.provider.domain.Resolution
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

        val pixels = pixelsOf(request.size)

        val response = try {
            client.generate(
                path = OpenAIImageEndpoint.GENERATIONS.path,
                request = OpenAIImageRequest(
                    model = target.modelName,
                    prompt = request.prompt,
                    // 호출 1건이 이미지 1장이다. 장수는 Task 개수로 나뉘어 들어온다.
                    n = 1,
                    size = "${pixels.width}x${pixels.height}",
                    quality = request.quality.value,
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
                        width = pixels.width,
                        height = pixels.height,
                        mimeType = "image/$OUTPUT_FORMAT",
                        fileSize = image.size,
                    ),
                    revisedPrompt = it.revisedPrompt,
                )
            },
        )
    }

    /**
     * 요청한 크기를 픽셀로 확정한다.
     *
     * [ImageSize.ByPixels] 는 이미 픽셀이라 그대로 쓴다. 사용자가 정확한 값을 말했으므로
     * 우리가 해석할 여지가 없다 — Provider 가 못 받으면 그건 거절이지 근사가 아니다.
     *
     * [ImageSize.ByRatio] 는 **긴 변** 기준으로 푼다 — `2k` 는 긴 쪽이 2048 이라는 뜻이고,
     * 짧은 쪽은 비율에서 나온다. 가로 기준으로 잡으면 세로 이미지의 픽셀 수가 비율마다 들쭉날쭉해진다.
     *
     * ```
     * 1:1  @ 1k  ->  1024x1024   두 변이 같으니 배율도 하나다
     * 16:9 @ 2k  ->  2048x1152   16 을 2048 로 만드는 128 배율을 9 에도 적용한다
     * 3:4  @ 1k  ->   768x1024   세로가 기니 세로가 1024 가 되고 가로가 따라 줄어든다
     * ```
     *
     * 주의: OpenAI 가 임의 크기를 받는지 고정 목록만 받는지 아직 실제 호출로 확인하지 않았다.
     * 고정 목록만 받는다면 이 함수가 "가장 가까운 허용 크기 고르기" 로 바뀐다.
     * 바뀌는 곳이 여기 한 곳이도록 계산을 가뒀다.
     */
    private fun pixelsOf(size: ImageSize): Pixels = when (size) {
        is ImageSize.ByPixels -> Pixels(size.width, size.height)
        is ImageSize.ByRatio -> {
            val longEdge = when (size.resolution) {
                Resolution.ONE_K -> 1024
                Resolution.TWO_K -> 2048
                Resolution.FOUR_K -> 4096
            }
            // 비율의 큰 쪽을 longEdge 에 맞추는 배율을 구해, 두 변에 똑같이 적용한다.
            // 16:9 를 2k 로 보내면 16 이 2048 이 되는 배율(128배)이 9 에도 걸려 1152 가 나온다.
            // 나눗셈을 마지막에 두는 이유는 정수 나눗셈의 절삭을 변마다 한 번씩만 일으키기 위해서다.
            val longerSide = maxOf(size.ratio.width, size.ratio.height)
            Pixels(
                width = longEdge * size.ratio.width / longerSide,
                height = longEdge * size.ratio.height / longerSide,
            )
        }
    }

    private data class Pixels(val width: Int, val height: Int)

    companion object {
        const val PROVIDER_NAME = "openai"

        /** png 로 고정한다. 투명 배경을 지원하는 유일한 형식이고, 저장 포맷 선택은 아직 제품 기능이 아니다. */
        const val OUTPUT_FORMAT = "png"
    }
}
