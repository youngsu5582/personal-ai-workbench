package dev.joyson.aiworkbench.upload.application

/** 동시 정정으로 판정을 마치지 못했으면 현재 상태로 다시 요청할 수 있다. */
class UploadStateChangedException : RuntimeException("파일 상태가 바뀌었다. 다시 요청한다")
