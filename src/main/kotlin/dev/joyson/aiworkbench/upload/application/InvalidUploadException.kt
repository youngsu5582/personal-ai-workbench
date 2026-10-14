package dev.joyson.aiworkbench.upload.application

/** 같은 파일에 맞지 않는 선언은 다시 전송해도 해결되지 않는다. */
class InvalidUploadException(message: String) : RuntimeException(message)
