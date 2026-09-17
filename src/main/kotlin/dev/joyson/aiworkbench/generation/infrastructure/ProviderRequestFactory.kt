package dev.joyson.aiworkbench.generation.infrastructure

import dev.joyson.aiworkbench.generation.domain.option.GenerationOption
import dev.joyson.aiworkbench.generation.domain.option.ImageSize
import dev.joyson.aiworkbench.generation.domain.option.Quality
import dev.joyson.aiworkbench.generation.domain.option.Resolution
import dev.joyson.aiworkbench.generation.domain.option.TextToImageOption
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ImageQuality
import org.springframework.stereotype.Component

/**
 * 우리 도메인 어휘를 Provider 포트의 어휘로 옮긴다.
 *
 * 이 방향으로만 번역한다. `provider` 는 `generation` 을 모르므로, 두 어휘를 잇는 책임은
 * 의존하는 쪽인 여기에 있다.
 *
 * [GenerationOption] 이 `sealed` 라 새 생성 종류를 추가하면 아래 `when` 이 컴파일 에러로 드러난다.
 */
@Component
class ProviderRequestFactory {

    fun from(option: GenerationOption): ExternalApiGenerateRequest = when (option) {
        is TextToImageOption -> {
            val (width, height) = pixelsOf(option.size)
            ExternalApiGenerateRequest(
                prompt = option.prompt,
                width = width,
                height = height,
                quality = qualityOf(option.quality),
            )
        }
    }

    /**
     * 크기를 픽셀로 확정한다.
     *
     * [ImageSize.ByPixels] 는 이미 픽셀이라 그대로 쓴다. 사용자가 정확한 값을 말했으므로
     * 해석할 여지가 없다 — Provider 가 못 받으면 그건 거절이지 근사가 아니다.
     *
     * [ImageSize.ByRatio] 는 **긴 변** 기준으로 푼다. 가로 기준으로 잡으면 세로 이미지의
     * 픽셀 수가 비율마다 들쭉날쭉해진다.
     *
     * ```
     * 1:1  @ 1k  ->  1024x1024   두 변이 같으니 배율도 하나다
     * 16:9 @ 2k  ->  2048x1152   16 을 2048 로 만드는 128 배율을 9 에도 적용한다
     * 3:4  @ 1k  ->   768x1024   세로가 기니 세로가 1024 가 되고 가로가 따라 줄어든다
     * ```
     */
    private fun pixelsOf(size: ImageSize): Pair<Int, Int> = when (size) {
        is ImageSize.ByPixels -> size.width to size.height
        is ImageSize.ByRatio -> {
            val longEdge = when (size.resolution) {
                Resolution.ONE_K -> 1024
                Resolution.TWO_K -> 2048
                Resolution.FOUR_K -> 4096
            }
            // 비율의 큰 쪽을 longEdge 에 맞추는 배율을 구해 두 변에 똑같이 적용한다.
            // 나눗셈을 마지막에 두는 이유는 정수 나눗셈의 절삭을 변마다 한 번씩만 일으키기 위해서다.
            val longerSide = maxOf(size.ratio.width, size.ratio.height)
            (longEdge * size.ratio.width / longerSide) to (longEdge * size.ratio.height / longerSide)
        }
    }

    /**
     * 제품 눈금을 포트 눈금으로 옮긴다.
     *
     * `valueOf(name)` 로 줄이지 않는 이유: 한쪽에 값을 추가하면 **런타임에** 터진다.
     * `when` 으로 두면 컴파일러가 빠진 가지를 알려준다.
     */
    private fun qualityOf(quality: Quality): ImageQuality = when (quality) {
        Quality.LOW -> ImageQuality.LOW
        Quality.MEDIUM -> ImageQuality.MEDIUM
        Quality.HIGH -> ImageQuality.HIGH
        Quality.XHIGH -> ImageQuality.XHIGH
        Quality.MAX -> ImageQuality.MAX
        Quality.AUTO -> ImageQuality.AUTO
    }
}
