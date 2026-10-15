package dev.joyson.aiworkbench

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ActuatorHealthTest @Autowired constructor(
    private val mockMvc: MockMvc,
) : IntegrationTest() {

    @Test
    fun `readiness probe is public and reports UP`() {
        mockMvc.get("/actuator/health/readiness")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UP") }
            }
    }
}
