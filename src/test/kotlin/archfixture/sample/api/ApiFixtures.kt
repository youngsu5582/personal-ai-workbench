package archfixture.sample.api

import archfixture.sample.application.FixtureWriter

/** [dev.joyson.aiworkbench.ArchitectureTest] 가 규칙이 위반을 실제로 잡는지 보려고 일부러 어긴 코드다. */
class FixtureController(private val writer: FixtureWriter)
