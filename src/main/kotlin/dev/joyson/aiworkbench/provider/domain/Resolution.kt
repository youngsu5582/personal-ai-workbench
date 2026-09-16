package dev.joyson.aiworkbench.provider.domain

import org.springframework.modulith.NamedInterface

import com.fasterxml.jackson.annotation.JsonValue

/**
 * 저장·전송 표기를 [value] 로 고정한다.
 *
 * 붙이지 않으면 상수 이름(`ONE_K`)이 그대로 JSON 과 DB 에 박혀서,
 * 나중에 상수를 리네임하는 순간 기존 행을 못 읽는다.
 */
@NamedInterface("domain")
enum class Resolution(@get:JsonValue val value: String) {
    ONE_K("1k"),
    TWO_K("2k"),
    FOUR_K("4k"),
}