package dev.joyson.aiworkbench.provider.openai

import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import java.util.Base64

/**
 * OpenAI Images API 전송 담당.
 *
 * 이 클래스가 아는 것은 OpenAI 의 HTTP 계약뿐이다 — 우리 도메인 타입을 import 하지 않는다.
 * 그 규칙이 지켜지는 한 나중에 별도 모듈로 옮기는 일이 파일 이동으로 끝난다.
 *
 * 이 API 는 **동기**다. 호출하면 수십 초 뒤 이미지가 응답 본문에 담겨 온다 —
 * 작업 식별자를 받아 폴링하는 방식이 아니다. 그래서 읽기 타임아웃이 넉넉해야 한다.
 *
 * 경로를 상수로 갖지 않고 인자로 받는 이유는 **세대마다 경로가 다르기** 때문이다.
 * 어느 경로로 보낼지는 모델이 정하는 일이라 어댑터가 풀어서 넘긴다([OpenAIImageModel]).
 */
class OpenAIImageClient(
    private val restClient: RestClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * @param path 보낼 엔드포인트 경로. `baseUrl` 기준 상대 경로다.
     */
    fun generate(path: String, request: OpenAIImageRequest): OpenAIImageResponse {
        log.debug("이미지 생성을 요청한다: path={} model={} n={}", path, request.model, request.n)

        return timed(request.model, request.n) {
            restClient.post().uri(path).body(request).readOrThrow(OpenAIImageResponse::class.java)
        }
    }

    /**
     * 입력 이미지를 함께 보내 고친다.
     *
     * 바이트 입력은 multipart, 주소가 포함된 입력은 JSON으로 보낸다.
     * 실패 처리는 [readOrThrow]로 공유한다.
     *
     * @param path 보낼 엔드포인트 경로. `baseUrl` 기준 상대 경로다.
     */
    fun edit(path: String, request: OpenAIImageEditRequest): OpenAIImageResponse {
        // 바이트는 남기지 않는다. 장수와 합계만 있으면 요청이 얼마나 무거웠는지는 알 수 있다.
        log.debug(
            "이미지 편집을 요청한다: path={} model={} 이미지={}장 {}bytes",
            path, request.model, request.images.size,
            request.images.filterIsInstance<OpenAIImageEditImage.Bytes>().sumOf { it.bytes.size },
        )

        return timed(request.model, request.n) {
            val post = restClient.post().uri(path)
            val spec = if (request.images.any { it is OpenAIImageEditImage.Url }) {
                post.contentType(MediaType.APPLICATION_JSON).body(jsonOf(request))
            } else {
                // boundary는 Spring이 붙인다.
                post.contentType(MediaType.MULTIPART_FORM_DATA).body(formOf(request))
            }
            spec.readOrThrow(OpenAIImageResponse::class.java)
        }
    }

    /**
     * 요청을 multipart 파트로 편다.
     *
     * 필드 이름과 폼 키의 대응은 **이 함수가 정본이다** — [OpenAIImageEditRequest] 는 multipart 로
     * 나가느라 Jackson 을 타지 않아, 애노테이션으로 이름을 말할 수 없다.
     */
    private fun formOf(request: OpenAIImageEditRequest): MultiValueMap<String, Any> {
        val form = LinkedMultiValueMap<String, Any>()
        form.add("model", request.model)
        form.add("prompt", request.prompt)
        // 폼 필드에는 타입이 없다. 숫자도 문자열로 실린다.
        request.n?.let { form.add("n", it.toString()) }
        request.size?.let { form.add("size", it) }
        request.quality?.let { form.add("quality", it) }
        request.outputFormat?.let { form.add("output_format", it) }

        request.images.forEach { input ->
            val image = input as OpenAIImageEditImage.Bytes
            // 파일명이 있어야 파트가 파일로 나간다. ByteArrayResource 의 getFilename() 은
            // 기본이 null 이라, 덮어쓰지 않으면 받는 쪽이 이것을 일반 필드로 읽는다.
            val resource = object : ByteArrayResource(image.bytes) {
                override fun getFilename(): String = image.filename
            }
            // 파트마다 형식을 말한다. 없으면 application/octet-stream 으로 나가고,
            // 형식 판별이 파일명 확장자에 맡겨진다.
            val headers = HttpHeaders().apply { contentType = MediaType.parseMediaType(image.contentType) }
            form.add(IMAGE_PART, HttpEntity(resource, headers))
        }
        return form
    }

    private fun jsonOf(request: OpenAIImageEditRequest) = OpenAIImageEditJsonRequest(
        model = request.model,
        prompt = request.prompt,
        images = request.images.map { image ->
            OpenAIImageReference(
                when (image) {
                    is OpenAIImageEditImage.Url -> image.url.toString()
                    // 주소와 바이트가 섞여도 이미지를 다운로드하거나 입력 순서를 바꾸지 않는다.
                    is OpenAIImageEditImage.Bytes ->
                        "data:${image.contentType};base64,${Base64.getEncoder().encodeToString(image.bytes)}"
                },
            )
        },
        n = request.n,
        size = request.size,
        quality = request.quality,
        outputFormat = request.outputFormat,
    )

    /**
     * 호출 1건의 소요 시간을 남긴다.
     *
     * 실패했을 때도 남긴다 — 타임아웃이 의심되는 순간에 필요한 숫자가 바로 이것이다.
     */
    private fun <T> timed(model: String, n: Int?, call: () -> T): T {
        val startedAt = System.nanoTime()
        try {
            return call()
        } finally {
            log.info(
                "이미지 생성 호출이 끝났다. model={} n={} 소요={}ms",
                model, n, (System.nanoTime() - startedAt) / 1_000_000,
            )
        }
    }

    companion object {
        /**
         * 입력 이미지를 싣는 파트 이름.
         *
         * 1장이어도 `image[]` 다 — 이 API 는 참조 이미지를 여러 장 받으므로 이름 자체가 배열이다.
         */
        const val IMAGE_PART = "image[]"
    }
}

