package dev.joyson.aiworkbench.provider.gemini

import dev.joyson.aiworkbench.provider.ExternalApiAudioProvider
import dev.joyson.aiworkbench.provider.ExternalApiAudioRequest
import dev.joyson.aiworkbench.provider.ExternalApiAudioResponse
import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.FailureKind
import dev.joyson.aiworkbench.provider.ProviderUsage
import dev.joyson.aiworkbench.provider.audio.Wav
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * 표준 읽기 요청을 Gemini Interactions 의 어휘로 옮기는 어댑터.
 *
 * 하는 일은 글 어댑터([GeminiTextProvider])와 같은 갈래다 — 모델 확인, 요청 번역, 실패 번역([GeminiFailures]),
 * 사용량을 해석하지 않고 싣기. 더해서 받은 소리의 길이를 바이트에서 읽는다.
 *
 * 형식은 `audio/wav` 로 요청한다. 길이를 바이트 수로 셀 수 있는 형식이어야 해서다.
 */
class GeminiAudioProvider(
    private val client: GeminiInteractionClient,
) : ExternalApiAudioProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override val name: String = GeminiFailures.PROVIDER_NAME

    override val models: Set<String> = GeminiAudioModel.entries.map { it.modelName }.toSet()

    override fun voices(model: String): Set<String> = if (supports(model)) VOICES else emptySet()

    override fun generateAudio(
        model: String,
        request: ExternalApiAudioRequest,
    ): ExternalApiAudioResponse = GeminiFailures.translate(log, model) {
        // supports · voices 를 건너뛰고 부를 수 있으므로 여기서도 확인한다. 다시 보내도 같으니 재시도 대상이 아니다.
        val target = GeminiAudioModel.find(model)
            ?: throw ExternalApiException(FailureKind.REJECTED, "다루지 않는 모델이다: $model")
        if (request.voice !in VOICES) throw ExternalApiException(FailureKind.REJECTED, "모르는 목소리다: ${request.voice}")

        val response = client.create(
            GeminiInteractionRequest(
                model = target.modelName,
                input = listOf(
                    GeminiStep(
                        type = USER_INPUT,
                        content = listOf(
                            GeminiStepContent(
                                type = TEXT,
                                text = request.text,
                                annotations = request.style?.let { listOf(GeminiSpeechAnnotation(SPEECH_METADATA, it)) },
                            ),
                        ),
                    ),
                ),
                responseFormat = GeminiResponseFormat(type = AUDIO, mimeType = Wav.MIME_TYPE),
                generationConfig = GeminiInteractionGenerationConfig(listOf(GeminiSpeechConfig(request.voice))),
            ),
        )

        assemble(response)
    }

    /**
     * 응답을 포트 타입으로 옮긴다.
     *
     * 끝나지 않은 상태는 설명되지 않아 다시 보내지 않는다 — 모르는 채로 다시 부르면 돈만 다시 나간다.
     * 끝났는데 소리가 없는 것은 다시 받으면 멀쩡할 수 있는 저쪽 사정으로 본다.
     * 소리가 왔는데 길이를 읽을 수 없으면 다시 받아도 같은 형식일 것이라 다시 보내지 않는다.
     */
    private fun assemble(response: GeminiInteractionResponse): ExternalApiAudioResponse {
        if (response.status != COMPLETED) {
            throw ExternalApiException(FailureKind.UNKNOWN, "설명되지 않는 응답 상태다: ${response.status} id=${response.id}")
        }
        val data = response.steps.orEmpty()
            .filter { it.type == MODEL_OUTPUT }
            .flatMap { it.content.orEmpty() }
            .lastOrNull { it.type == AUDIO }?.data
            ?: throw ExternalApiException(FailureKind.PROVIDER_ERROR, "응답에 소리가 없다 id=${response.id}")

        val audio = Base64.getDecoder().decode(data)
        val durationSeconds = Wav.durationSecondsOf(audio)
            ?: throw ExternalApiException(
                FailureKind.UNKNOWN,
                "소리의 길이를 읽지 못했다 — PCM WAV 가 아니다. 앞 12바이트=${audio.take(12).joinToString("") { "%02x".format(it) }} id=${response.id}",
            )

        return ExternalApiAudioResponse(
            audio = audio,
            mimeType = Wav.MIME_TYPE,
            durationSeconds = durationSeconds,
            usage = response.usage?.let { ProviderUsage(raw = it) },
            providerId = response.id,
        )
    }

    private companion object {
        const val USER_INPUT = "user_input"
        const val MODEL_OUTPUT = "model_output"
        const val TEXT = "text"
        const val AUDIO = "audio"
        const val SPEECH_METADATA = "speech_metadata"
        const val COMPLETED = "completed"

        /** 문서가 밝힌 30개다. 모델마다 다르다는 말이 없어 두 모델이 같은 목록을 쓴다. */
        val VOICES = setOf(
            "Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede", "Callirrhoe", "Autonoe",
            "Enceladus", "Iapetus", "Umbriel", "Algieba", "Despina", "Erinome", "Algenib", "Rasalgethi", "Laomedeia", "Achernar",
            "Alnilam", "Schedar", "Gacrux", "Pulcherrima", "Achird", "Zubenelgenubi", "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat",
        )
    }
}
