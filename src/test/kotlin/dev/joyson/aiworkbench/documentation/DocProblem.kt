package dev.joyson.aiworkbench.documentation

/** 문서의 몇째 줄이 무엇을 잘못 가리키는가. */
internal data class DocProblem(val line: Int, val message: String)
