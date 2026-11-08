package dev.joyson.aiworkbench.generation.application

/** 템플릿을 저장하거나 실행할 수 없는 요청이다. 변수 · 입력 이미지 불일치, 종류 변경, 규칙을 어긴 치환 결과가 여기에 든다. */
class InvalidTemplateException(message: String) : RuntimeException(message)
