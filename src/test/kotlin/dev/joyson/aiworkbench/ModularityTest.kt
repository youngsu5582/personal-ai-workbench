package dev.joyson.aiworkbench

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModularityTest {
    @Test
    fun `애플리케이션 모듈 간 의존성 경계가 유효하다`() {
        ApplicationModules.of(PersonalAiWorkbenchApplication::class.java).verify()
    }
}
