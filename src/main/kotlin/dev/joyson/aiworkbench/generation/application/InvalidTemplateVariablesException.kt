package dev.joyson.aiworkbench.generation.application

/** 변수의 누락·추가 또는 빈 치환 결과로 시스템 지시문을 완성할 수 없을 때 사용한다. */
class InvalidTemplateVariablesException(message: String) : RuntimeException(message)
