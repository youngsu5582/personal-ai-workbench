package archfixture.sample.domain

import archfixture.sample.application.FixtureCommand

/** [dev.joyson.aiworkbench.ArchitectureTest] 가 규칙이 위반을 실제로 잡는지 보려고 일부러 어긴 코드다. */
class FixtureRepository {
    fun save(command: FixtureCommand) = command
}

interface FixtureFinder
