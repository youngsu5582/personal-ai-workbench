package dev.joyson.aiworkbench.provider.openai

import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper

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

    /** 실패 본문을 원문으로 받아둔 뒤 파싱하기 위해 직접 읽는다. */
    private val mapper = jacksonObjectMapper()

    /**
     * @param path 보낼 엔드포인트 경로. `baseUrl` 기준 상대 경로다.
     */
    fun generate(path: String, request: OpenAIImageRequest): OpenAIImageResponse {
        log.debug("이미지 생성을 요청한다: path={} model={} n={}", path, request.model, request.n)

        return timed(request.model, request.n) {
            exchange(restClient.post().uri(path).body(request))
        }
    }

    /**
     * 입력 이미지를 함께 보내 고친다.
     *
     * [generate] 와 메서드가 갈리는 이유는 **전송 형식이 다르기** 때문이다 —
     * 파일을 싣는 방법이 JSON 밖에 있어 경로만 바꿔서는 보낼 수 없다.
     * 대신 실패 처리는 [exchange] 로 공유한다.
     *
     * @param path 보낼 엔드포인트 경로. `baseUrl` 기준 상대 경로다.
     */
    fun edit(path: String, request: OpenAIImageEditRequest): OpenAIImageResponse {
        // 바이트는 남기지 않는다. 장수와 합계만 있으면 요청이 얼마나 무거웠는지는 알 수 있다.
        log.debug(
            "이미지 편집을 요청한다: path={} model={} 이미지={}장 {}bytes",
            path, request.model, request.images.size, request.images.sumOf { it.bytes.size },
        )

        return timed(request.model, request.n) {
            exchange(
                restClient.post()
                    .uri(path)
                    // boundary 는 Spring 이 붙인다. 직접 적으면 본문과 어긋날 수 있다.
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(formOf(request)),
            )
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

        request.images.forEach { image ->
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

    /**
     * 상태를 보고 응답을 읽거나 실패로 바꾼다.
     *
     * 전송 형식이 갈려도(JSON·multipart) **에러 규약은 하나여야** 하므로 여기 한 곳에 둔다.
     * 두 벌이 되면 한쪽만 고쳐지고, 그 차이는 장애가 났을 때에야 드러난다.
     */
    private fun exchange(spec: RestClient.RequestHeadersSpec<*>): OpenAIImageResponse =
        spec.exchange { _, response ->
            val status = response.statusCode
            if (status.is2xxSuccessful) {
                response.bodyTo(OpenAIImageResponse::class.java)
                    ?: throw OpenAIException(status, "응답 본문이 비어 있다")
            } else {
                // 본문을 먼저 문자열로 받는다. 곧바로 DTO 로 읽으면 파싱에 실패했을 때
                // 스트림이 이미 소비돼 원문을 다시 볼 수 없다 — 그러면 "요청이 실패했다" 만 남아
                // 무엇이 잘못됐는지 알 수 없다.
                val raw = runCatching { response.bodyTo(String::class.java) }.getOrNull()
                val parsed = raw?.let {
                    // 실패 본문은 {"error": {...}} 형태다. 아니면(HTML 오류 페이지 등) 원문을 그대로 남긴다.
                    runCatching { mapper.readValue(it, OpenAIErrorEnvelope::class.java).error?.message }.getOrNull()
                }
                throw OpenAIException(status, parsed ?: raw?.take(500)?.ifBlank { null } ?: "본문이 비어 있다")
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

/**
 * OpenAI 호출 실패.
 *
 * 상태 코드를 그대로 들고 있는 이유는 **재시도 판단이 호출자의 몫**이기 때문이다.
 * 429·5xx 는 다시 시도할 만하고 4xx 는 아닌데, 그 정책은 전송이 정할 일이 아니다.
 */
class OpenAIException(
    val status: HttpStatusCode,
    /**
     * OpenAI 가 준 설명.
     *
     * [message] 와 따로 두는 이유는 `message` 에 상태 코드를 함께 담기 위해서다.
     * `message` 를 이 값으로 덮으면 상태 코드가 로그와 상위 예외에서 사라진다.
     */
    val detail: String,
) : RuntimeException("[$status] $detail") {

    /** 다시 시도해볼 만한 실패인가. 판단 재료일 뿐, 재시도 여부는 호출자가 정한다. */
    val retryable: Boolean
        get() = status.value() == 429 || status.is5xxServerError
}
