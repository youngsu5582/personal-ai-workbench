# Plan: 없는 버킷에 PUT 할 때 응답이 연결 실패로 바뀌는 것 (#44)
Created: 2026-10-11
Status: completed

## 확인된 사실 (탐침 20회 × 본문 크기 4종, Garage v2.4.1)

SDK 2.55.1 은 PutObject 본문이 **1 MiB 이상일 때만** `Expect: 100-continue` 를 붙인다 (`S3Configuration` 기본 threshold 1_048_576).

| 본문 | UrlConnection | Apache 4 | Apache 5 (threshold 기본 1 MiB) | Apache 5 + threshold 0 |
|---|---|---|---|---|
| 4 B · 64 KiB | 20/20 NoSuchBucket | 20/20 | 20/20 | 20/20 |
| 1 MB (SDK 기본으론 헤더 없음) | 18 NoSuchBucket · **2 "Error writing request body" retryable=true** | 20/20 | 20/20 (본문 다 보내고 137ms) | 20/20 (**본문 안 보내고 3ms**) |
| 2 MiB (헤더 있음) | 20/20 상태 404 이지만 **오류코드 없음(`? 404`)** — JDK 가 거절 응답 본문을 버린다 | 20/20 | 20/20 | 20/20 |
| 정상 PUT 64 KiB / 2 MiB | 5ms / 28ms | 6ms / 24ms | 5ms / 28ms | 5ms / 25ms |

- **`s3:2.55.1` 은 `apache5-client` 를 전이 의존(runtime)으로 이미 가져온다.** Apache 5 로 가면 새 라이브러리가 0개, `url-connection-client` 만 빠진다. 컴파일에서 클래스를 참조하므로 선언은 명시해야 한다
- Apache 4(`apache-client`)는 httpclient 4.5.13 · httpcore 4.4.16 이 **새로** 들어온다. Apache 5 가 있으니 고를 이유가 없다
- Apache 5 는 Expect 헤더를 스스로 붙이지 않는다(SDK javadoc). 작은 본문까지 "판정을 받고 본문을 보내는" 쪽으로 고정하려면 `expectContinueThresholdInBytes(0)`

- 이슈의 가설("Expect 헤더 때문")은 반대였다. 4 B 본문엔 헤더가 **없고**, 없어서 본문을 보내는 중에 서버가 닫으면 응답을 잃는다
- `expectContinueEnabled(false)` 는 이미 없는 헤더를 끄는 것이라 효과가 없다
- UrlConnection 은 헤더가 있어도 문제다 — SDK 가 "Apache 를 쓰면 이 제약이 사라진다" 고 스스로 적어 뒀다

## 결정
- D1 HTTP 클라이언트를 `Apache5HttpClient` 로 + `expectContinueThresholdInBytes(0)`. 라이브러리는 이미 runtime 에 있었고 `url-connection-client` 선언만 빠진다

## Progress
- [x] 원인 확정 — SDK 소스(StreamingRequestInterceptor · UrlConnectionHttpClient.RequestCallable) + 로컬 재현
- [x] Apache 4 · Apache 5 · Apache 5+threshold 0 모두 탐침 80/80 통과
- [x] 사용자 확인 (D1)
- [x] `S3FileStorageTest` 에 회귀 테스트 `없는 버킷은 본문이 커도 거절이 먼저 온다` — 1 MB 와 2 MiB
- [x] `S3Clients.of` 주석 · `build.gradle.kts` 주석 갱신 (왜 Apache 5 인가, 왜 threshold 0 인가)
- [x] 탐침 테스트 삭제, 전체 테스트 274개 통과
- [ ] 커밋, PR (`closes #44`)

## 남는 한계
- 회귀 테스트의 2 MiB 쪽은 옛 클라이언트에서 결정적으로 빨갛다(오류 코드 소실). 1 MB 쪽은 흔들림을 잡는 것이라 옛 클라이언트에서도 가끔 초록이다
- `expectContinueThresholdInBytes(0)` 을 지워도 테스트는 초록일 수 있다 — Apache 5 는 헤더 없이도 조기 응답을 읽는 것으로 보인다(20/20). 0 은 "본문을 보내지 않는다" 를 못 박는 용도다

## Resume Point
없음. PR 머지로 끝난다.

## Files
- INDEX.md — 이 문서
