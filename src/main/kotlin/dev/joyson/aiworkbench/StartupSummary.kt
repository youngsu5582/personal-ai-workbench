package dev.joyson.aiworkbench

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 기동할 때 **이 앱이 무엇으로 동작하는지** 한 줄로 말한다.
 *
 * 설정은 `application.yaml`·`.env`·실제 환경변수 여러 곳에서 오고 우선순위가 있다. 그래서
 * "지금 이 앱이 어디에 쓰고 있나" 가 로그 없이는 확인되지 않는다 —
 * 저장 실패를 쫓다가 보관소도 자격증명도 멀쩡한데 **실패한 앱이 그 설정으로 떴는지**를
 * 확인할 방법이 없어 원인을 못 좁힌 적이 있다.
 *
 * ### 설정이 아니라 **고른 결과**를 적는다
 *
 * `@ConfigurationProperties` 는 쓰이든 말든 전부 등록된다. 그것들을 그대로 찍으면
 * 로컬로 띄운 앱에서도 s3 설정이 같이 보여서 **어느 쪽이 살았는지는 여전히 모른다.**
 * 전체 설정 덤프가 필요하면 그건 actuator 의 `configprops` 가 할 일이다.
 *
 * ### 비밀은 값이 아니라 여부만
 *
 * 로그는 남의 눈에 띄기 쉬운 자리다. 다만 "키가 빠졌다" 와
 * "역할 기반 인증이라 일부러 비웠다" 는 구분되어야 한다.
 *
 * 구현은 **자기 모듈 안**에 둔다. 한곳에 모으면 그 클래스가 모든 모듈의 내부 설정을
 * 들여다보게 되고, 그건 모듈 경계가 막으려는 바로 그것이다.
 */
fun interface StartupSummary {
    fun describe(): String
}

/**
 * 등록된 [StartupSummary] 를 모아 찍는다. **무엇이 있는지는 모른다.**
 *
 * `List<T>` 로 받아 각자가 자기를 설명하는 것은 이 저장소의 `ProviderRegistry` 와 같은 모양이다.
 * 모듈을 더해도 이 클래스는 안 바뀐다.
 *
 * `ApplicationReadyEvent` 가 아니라 [PostConstruct] 인 이유: 기동이 뒤에서 실패해도
 * **무엇으로 뜨려 했는지**는 남아야 한다.
 */
@Component
class StartupLogger(
    private val summaries: List<StartupSummary>,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun logAll() = summaries.forEach { log.info("{}", it.describe()) }
}
