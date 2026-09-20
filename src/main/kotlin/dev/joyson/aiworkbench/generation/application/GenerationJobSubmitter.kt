package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.option.GenerationOption
import dev.joyson.aiworkbench.generation.domain.option.ImageToImageOption
import dev.joyson.aiworkbench.generation.domain.option.TextToImageOption
import dev.joyson.aiworkbench.generation.infrastructure.ImageSourceFinder
import dev.joyson.aiworkbench.provider.ProviderRegistry
import org.springframework.stereotype.Service
import java.util.UUID
import org.springframework.transaction.annotation.Transactional

/**
 * 생성 요청을 접수한다 — 받아들일지 판단하고, 받아들이기로 했으면 기록을 만든다.
 *
 * [GenerationJobWriter] 와 나눠 둔 이유는 책임이 다르기 때문이다.
 * 쓰기는 "어떻게 저장하나" 고 여기는 **"받아도 되는 요청인가"** 다.
 * 앞으로 늘어날 규칙(크레딧 차감, 요금제별 한도)이 갈 곳도 여기지 쓰기 쪽이 아니다.
 *
 * 트랜잭션 경계를 여기 두는 이유: 나중에 크레딧을 깎게 되면 차감과 기록이 원자적이어야 한다.
 * 한쪽만 되면 크레딧은 빠졌는데 작업이 없는 상태가 생긴다.
 */
@Service
class GenerationJobSubmitter(
    private val generationJobWriter: GenerationJobWriter,
    private val providerRegistry: ProviderRegistry,
    private val imageSourceFinder: ImageSourceFinder,
) {

    /**
     * 검증이 기록보다 먼저다. 공짜로 막을 수 있는 것을 먼저 막아야
     * 나중에 크레딧을 깎게 됐을 때 환불할 일이 안 생긴다.
     *
     * 컨트롤러가 아니라 여기서 확인하는 이유: 호출 경로가 늘어나면(배치 등록·재실행)
     * 호출자마다 같은 검사를 기억해야 하고, 한 곳만 빠뜨려도 조용히 통과한다.
     */
    @Transactional
    fun submit(ownerUuid: UUID, command: GenerationCommand): GenerationJobView {
        providerRegistry.find(command.model)
            ?: throw UnsupportedModelException(command.model, providerRegistry.availableNames)

        requireOwnedSources(ownerUuid, command.option)

        return generationJobWriter.create(ownerUuid, command)
    }

    /**
     * 고치겠다고 지목한 이미지가 실제로 있고 내 것인지 본다.
     *
     * 여기서 막지 않으면 202 로 접수된 뒤 몇 분 지나 Task 실패로만 드러난다.
     * 그 실패는 결정적이라 다시 해도 같고, 큐 자리만 태운다.
     *
     * **바이트는 읽지 않는다.** 이 메서드는 트랜잭션 안이고, 보관소 읽기가 거기 들어오면
     * 커넥션을 그 시간만큼 붙잡는다. 있는지 묻는 데는 메타 행이면 충분하다.
     *
     * 장수 제한은 여기서 다시 보지 않는다 — 옵션 생성자가 이미 막았고, 거기서 걸리면 400 이다.
     */
    private fun requireOwnedSources(ownerUuid: UUID, option: GenerationOption) {
        val sources = when (option) {
            // 입력을 요구하지 않는 종류다. 종류가 늘면 이 when 이 컴파일 에러로 알려준다.
            is TextToImageOption -> return
            is ImageToImageOption -> option.sources
        }

        sources.firstOrNull { imageSourceFinder.findOwned(it, ownerUuid) == null }
            ?.let { throw UnknownImageSourceException(it.uuid) }
    }
}
