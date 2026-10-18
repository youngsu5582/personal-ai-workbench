package dev.joyson.aiworkbench

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.Test

/**
 * 쿠버네티스 probe 가 보는 창구.
 *
 * kubelet 은 200~399 를 모두 성공으로 본다. 이 경로가 인증 대상이 되면 로그인 리다이렉트를 받고도
 * probe 는 통과하므로, 토큰 없이 열려 있는지는 여기서만 드러난다.
 *
 * management 설정은 Boot 기본값을 그대로 쓴다. 테스트는 application.yaml 을 읽지 않으므로,
 * 기본값에 기대야 여기서 본 동작이 배포에서도 같다.
 */
class ActuatorHealthTest @Autowired constructor(
    private val mockMvc: MockMvc,
) : IntegrationTest() {

    @ParameterizedTest
    @ValueSource(strings = ["/actuator/health/readiness", "/actuator/health/liveness"])
    fun `kubelet 은 토큰 없이 probe 경로를 본다`(path: String) {
        mockMvc.get(path).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("UP") }
        }
    }

    @Test
    fun `health 는 하위 구성요소를 드러내지 않는다`() {
        mockMvc.get("/actuator/health").andExpect {
            status { isOk() }
            jsonPath("$.components") { doesNotExist() }
        }
    }
}
