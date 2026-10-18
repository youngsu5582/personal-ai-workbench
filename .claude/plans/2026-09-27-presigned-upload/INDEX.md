# Plan: 보관소에 직접 올리는 업로드 (presigned PUT)

Created: 2026-09-27
Status: active
코드와 대조: 2026-10-11 (base main — #39 · #40 · #41 · #43 머지 후)

## 무엇을 만드나

사용자가 가진 이미지를 **앱을 거치지 않고 보관소에 직접** 올리고, 그것을 i2i 입력으로 쓴다.

**완료 기준(1문장)**: 발급받은 주소로 이미지를 올리면, 그 uuid 를 `FileSource.Uploaded` 로 참조해 i2i 를 돌릴 수 있다.

앱이 바이트를 받지 않는 것이 이 방식을 고른 이유 전부다. `MultipartFile` 은 힙에 통째로 올라오는데, 그건 결과물을 내려보낼 때 #35 에서 막 걷어낸 모양이다.

```
POST /api/uploads          → PUT 주소 + uuid, PENDING 행
PUT → Garage               앱을 거치지 않는다
POST /api/jobs  sources:[{"type":"uploaded","uuid":…}]
   └ 트랜잭션 밖: PENDING 이면 Range GET 1번 → 매직넘버 → READY / 불합격이면 REJECTED + 객체 삭제 → 400
   └ 트랜잭션 안: Job·Task 기록
워커가 실행 시점에 읽기 주소를 발급 → Provider
```

## 확인된 사실

실험으로 — Garage `v2.4.1` 에 실제로 쏘아 확인했다.

| # | 사실 | 무엇을 정했나 |
|---|---|---|
| F1 | `contentType` 이 서명에 들어간다. 다르게 올리면 **거부되고 보관소에 남지도 않는다** | 형식 선언을 발급 시점에 못박는다 |
| F2 | `contentLength` 도 서명에 들어간다 (`SignedHeaders=content-length;content-type;host`) | 크기도 발급 시점에 못박는다 |
| F3 | **같은 주소로 다시 PUT 하면 덮어써진다** (1차·2차 모두 200) | 검증 후 바꿔치기가 가능하다 |
| F4 | Garage 는 조건부 쓰기(`If-None-Match: *`)를 **지원하지 않는다** (400) | 1회용 주소로 F3 을 막을 수 없다 |
| F5 | `contentMD5` 를 서명에 넣으면 **내용이 고정된다.** 내용을 바꾸면 보관소가 400, 해시를 내용에 맞게 고치면 서명이 깨져 403 | F3 을 구조적으로 막을 수 있다 (해시 PR) |
| F6 | 범위 요청(`Range: bytes=0-11`)에 `Content-Range` 를 준다. 객체보다 큰 범위면 있는 만큼, 없는 키를 지워도 성공 (`S3FileStorageTest` 가 고정한다) | 요청 한 번으로 크기와 앞부분을 함께 읽는다 |
| F7 | `x-amz-checksum-sha256` 도 서명에 실으면 **강제된다** — 다른 내용 400 `InvalidDigest`, 해시를 고치면 403, 헤더를 빼면 400. 원본이 있는 자리에 같은 주소로 다른 내용을 올려도 400 이고 원본이 남는다 | 해시 PR 은 SHA-256 하나로 키와 강제를 겸한다 |
| F8 | (라이브) OpenAI 는 `localhost` 보관소의 읽기 주소를 받아가지 못한다 — `400 invalid_value` "Error while downloading file. Upstream status code: 407." | 주소로 실패하면 바이트로 다시 보낸다(D13) |

공식 문서로 — [S3 compatibility](https://garagehq.deuxfleurs.fr/documentation/reference-manual/s3-compatibility/)

- 버킷 이벤트 알림(`PutBucketNotificationConfiguration`)은 **Missing** 이다 → 보관소가 업로드 완료를 알려줄 수 없다
- Lifecycle 은 `Expiration`·`AbortIncompleteMultipartUpload` 만, prefix 는 `Filter` 안에서만 받는다
- Range GET 과 `x-amz-checksum-sha256` 강제는 문서에 없었지만 F6·F7 로 확인했다

## 결정 사항

| # | 결정 | 이유 |
|---|---|---|
| D1 | 발급 포트를 `PresignedUrlIssuer` 와 **따로** 둔다 | 방향이 반대이고 위험도 반대다. 내주는 주소가 새면 남이 보고, 받는 주소가 새면 남이 **쓴다** |
| D2 | 서명에 `key` · `contentType` · `contentLength` 를 싣는다 | F1·F2. 발급 시점에 막을 수 있는 전부다 |
| D3 | **내용은 올라온 뒤에 본다** — 앞 12바이트 매직넘버 | 해시를 서명에 넣어도 "말한 그 바이트"를 고정할 뿐 "그게 png 다"는 보장하지 못한다 |
| D4 | **최종 위치로 옮기지 않는다** | 내용이 서명에 묶이면(F5) 바꿔치기가 구조적으로 불가능해 이동이 막던 것이 일어나지 않는다 |
| D5 | 쓰기 수명을 읽기와 **나누지 않는다** | 느린 회선에서 실제로 짧아 실패하면 그때 나눈다 (`StorageConfig` 의 업로드 발급자 KDoc) |
| D6 | 검증 전에는 **읽기 주소를 발급하지 않는다** | 검증을 통과하지 못한 객체는 열람 경로가 없어야 한다. `status` 의 목적은 노출 통제다 |
| D8 | **complete API 를 두지 않는다.** 첫 참조(i2i 접수) 때 트랜잭션 밖에서 검증하고 결과를 `status` 에 남긴다 | 보관소를 읽는 일은 같고 시점만 다르다. complete 가 더하는 것은 "올렸다" 신호 왕복 1번, 공개 계약, 클라이언트의 순서 의무다. 업로드 직후 결과를 보여줄 화면이 생기면 같은 판정 함수를 부르는 얇은 엔드포인트로 붙인다 |
| D9 | 상태 전이는 **조건부 UPDATE** (`… WHERE status = 'PENDING'`) | 판정은 HTTP 요청마다 불려 "부르는 쪽이 하나" 라는 전제가 처음부터 없다. 첫 참조가 겹치고 F3 이 끼면 READY 인데 객체가 없는 행이 생긴다. 선례 `closeIfAllTasksFinished` |
| D7 | 해시를 받으면 **키에 해시를 넣되 결과물(`blobs/`)과 자리를 나눈다** — `users/{owner}/uploads/ab/cd/{hash}.{ext}` | 같은 자리면 업로드 행과 결과물 행이 한 객체를 공유해 삭제에 참조 세기가 필요하다. 키 규칙(`BlobKey`)은 공유하고 객체는 공유하지 않는다. **해시 PR** |
| D10 | 중복 판정은 `(owner_user_uuid, hash)` unique. **밖으로 내는 식별자는 uuid** | 형식은 바이트가 정하므로 정체성은 해시다. 선언한 형식은 주장일 뿐이다. 해시를 식별자로 내보내면 알고리즘이 계약에 박힌다. **해시 PR** |
| D11 | 키에 `v2/` 같은 판 표시를 붙이지 않는다 | 키는 행마다 통째로 저장돼 옛 모양과 새 모양이 함께 산다. 앞에 붙이면 "계정 정리는 `users/{owner}/` 접두사 하나" (`BlobKey`) 가 깨진다 |
| D12 | 판정은 **있는가 · 선언한 크기인가 · 매직넘버** 까지. 디코드·재인코딩은 하지 않는다 | 이 서버는 이미지를 열지 않고 Provider 가 디코드한다. 판정의 목적은 보안 경계가 아니라 이른 거절과 노출 통제(D6)다. 재인코딩은 앱이 바이트를 받아야 해 presigned 를 고른 이유와 부딪친다. OWASP 는 매직넘버만으로는 부족하다고 한다 — 우리가 파일을 열게 되면 다시 본다 |
| D13 | 주소로 넘긴 입력을 Provider 가 받아가지 못하면 **같은 입력을 바이트로 한 번 더** 보낸다 | 운영 보관소는 직접 업로드 때문에 어차피 공개돼야 해 대개 주소로 된다. 그래도 일시적으로 못 받아가는 경우를 견딘다. 어댑터는 `INPUT_UNREACHABLE` 이라는 사실만 답하고 다시 보낼지는 워커가 정한다. 가르는 근거는 문구다 — `code` 는 `invalid_value` 로 일반적이다(F8). 지출 기록에는 과금 없는 거절(`REJECTED`)로 남는다 |

결정이 정한 모양 — U1 은 **한 행 + `status`**(`PENDING`·`READY`·`REJECTED`), U3 은 **`upload` 모듈 + 루트 포트**다.
generation 은 루트 포트로 "쓸 수 있는 내 파일인가" 와 "읽기 주소" 만 묻는다. **보관소 키는 경계를 넘지 않는다.**

### D7 은 원래 `blobs/` 공유였다

처음에는 업로드 키를 결과물과 같은 `blobs/{sha256}` 에 두고 "보관소가 키 = 내용을 보장한다" 고 봤다.
그런데 서명에 싣는 것은 MD5(F5)고 키는 SHA-256 이다. 보관소는 MD5 만 확인하므로 키의 SHA-256 은 클라이언트의 말이다.
그러면 내려받은 결과물의 SHA-256 을 대고 다른 바이트를 올려 **결과물 객체를 덮어쓸 수 있다.** 자리를 나누면 이 길이 없다.

### 잘못 선언한 형식은 다시 열지 않는다 (해시 PR)

이름이 `.png` 인 JPEG 는 해시는 같고 선언만 틀린다. `(owner, hash)` 가 REJECTED 로 남으면 그 사용자는 같은 내용을 다시 올릴 수 없다.

- 클라이언트는 확장자가 아니라 **매직넘버로 선언**한다 — 해시를 내려고 어차피 전체를 읽는다. `upload.sh` 와 샘플 페이지가 그렇다
- REJECTED 에는 형식을 바꿔 와도 **400 과 사유**만 돌려준다. 서버가 선언을 정정해 주지 않는다
- 올바른 선언은 클라이언트 쪽에서 보장한다. 외부 소비자가 생기면 같은 로직을 SDK 로 제공한다

처음에는 다른 형식으로 오면 그 행을 PENDING 으로 다시 열었다(`REJECTED → PENDING`).
조건부 UPDATE 하나면 될 줄 알았지만, 정정 전 판정이 정정된 PENDING 을 덮는 경우 때문에 `updated_at` 대조가,
정정끼리 겹치는 경우 때문에 재시도 루프가 붙었다. 공식 클라이언트에서는 생기지 않는 경로 위의 경합이라 걷어냈다.
대가는 잘못 선언해 거절된 내용이 그 사용자에게 영구히 막힌다는 것이다. 실제로 겪는 소비자가 생기면 운영자가 행을 지워 풀고, 그때 다시 검토한다.

## 단계와 PR

```
main   (#40 리포지토리 포트 · #41 규약 + ArchitectureTest · #43 Spring Data Repository 포트 — 머지됨)
 └─ #45 feat/upload-records     기록 + 판정 (upload 모듈)
     └─ #46 feat/upload-i2i      i2i 동작 + 스크립트 + 문서
         └─ 해시 PR (별도)        내용 고정 + 중복 방지
```

관심사로 나눈다. 기록과 판정은 upload 모듈 안에서 혼자 설명되고, i2i 연결은 generation 의 접수와 워커를 고친다.
문서는 i2i 까지 이어진 흐름을 설명하므로 i2i PR 에 둔다.

입력 파일 확인은 `FileSourceResolver.isUsable`·`prepare` 의 두 `when` 이 종류별로 가른다. 올린 파일은 upload 모듈의 `UploadedFiles` 에 묻는다.

## Progress

- [x] **1단계** 발급 포트 + S3 구현 + 발급 API `POST /api/uploads` (#36)
- [x] **#45** `feat/upload-records` — 기록 + 판정
  - [x] 1. 올라온 파일을 기록한다 — `V6__uploaded_files.sql` + `UploadedFile` + `UploadedFileRepository`(Spring Data 가 구현), 발급이 PENDING 행을 남긴다
  - [x] 2. 보관소가 객체의 일부와 전체 크기를 알려 준다 — `FileStorage.readRange` · `delete`
  - [x] 3. 올라온 파일이 쓸 만한지 upload 모듈이 판정한다 — 루트 포트 `UploadedFiles.findUsable` + `ImageContentType.detect` + 조건부 전이(`UploadedFileWriter.settle`)
- [x] **#46** `feat/upload-i2i` — #45 위에 쌓는다
  - [x] 4. 올린 이미지를 i2i 입력으로 쓴다 — `FileSource.Uploaded`, 접수 검증을 트랜잭션 밖으로, 읽기 주소는 `UploadedFiles.issueReadUrl`, 보관소 장애는 503
  - [x] 5. 스크립트(`SOURCE_TYPE=uploaded`)와 문서(`direct-upload.md` 재구성)
  - [x] 6. 폴백 — 주소로 실패하면 바이트로 다시 보낸다(D13)
  - [x] 라이브 확인 — 로컬 Garage + OpenAI 로 `upload.sh` → `i2i.sh`. 주소 실패(1.1초, 과금 없음) → 바이트로 성공(12.3초)
- [x] **해시 구현** — `feat/upload-checksum`
  - [x] Garage 실험 — SHA-256 도 강제된다(F7). SHA-256 으로 간다
  - [x] 클라이언트가 보낼 헤더 둘(`x-amz-checksum-sha256`·`x-amz-sdk-checksum-algorithm`)을 발급 응답과 버킷 CORS 에
  - [x] 서명에 해시(F7) + `V7` nullable 해시 컬럼 + `(owner_user_uuid, hash)` unique
  - [x] 키를 D7 로, 중복이면 기존 uuid 를 돌려준다. PENDING 중복은 발급 때 보관소를 한 번 보고 판정한다
- [ ] (선택) 한 번도 참조되지 않은 PENDING 정리 — DB 기준. 자리를 옮기지 않으므로(D4) prefix lifecycle 로는 READY 까지 지워진다
- [ ] (외부 소비자가 생기면) 업로드 클라이언트 SDK — 매직넘버 선언·해시·체크섬 헤더를 한 곳에. 지금은 `upload.sh`·샘플 페이지 두 구현이 있다

## 함정

- **로컬 보관소(`localhost`) 주소는 Provider 가 받아가지 못한다** — 폴백이 바이트로 보내 동작은 하지만, 요청마다 실패 1건(약 1초)이 먼저 지출 기록에 남는다
- **jOOQ 가 마이그레이션 SQL 을 H2 파서로 읽는다** — 인덱스 `DESC` 금지, 복합 `ALTER` 금지. 부분 인덱스(`WHERE`)도 위험하다
- **`GenerationJobService.submit` 의 `@Transactional`** — 판정이 보관소를 타므로 검증을 트랜잭션 밖으로 빼야 한다. 크레딧과 기록의 원자성은 `GenerationJobWriter.create` 의 트랜잭션이 맡는다
- **`ByteArrayResource.getFilename()` 은 기본이 null** — override 하지 않으면 파트가 파일로 안 나가고, 증상은 저쪽의 400 이라 원인이 멀다
- **`spring.servlet.multipart` 설정은 이 경로에 필요 없다** — 앱이 파일을 받지 않는다
- **`contentLength` 를 서명에 넣으면 스트리밍 업로드가 불가능해진다** — 올리는 쪽이 정확한 크기를 미리 알아야 한다
- **기존 발급 URL 에 해시를 소급해 넣을 수는 없다** — 해시 구현 이후 신규 주소는 내용이 고정된다. 기존 행은 nullable 해시와 UUID 경로로 유지한다

## Resume Point

**해시 구현 완료** — 워크트리 `upload-checksum`, 브랜치 `feat/upload-checksum`.
- 신규 발급 요청은 소문자 hex `sha256` 이 필수. 서명에는 원본 digest 의 Base64 를 쓴다.
- READY 는 `uploadRequired=false` 와 기존 UUID, PENDING 은 주소를 재발급하고 올라온 객체가 있으면 판정한다.
- REJECTED 는 형식을 바꿔 와도 400 과 사유만 돌려준다. 다시 열지 않는다.
- 동시 최초 발급은 유니크 제약에 걸린 쪽이 트랜잭션 밖에서 다시 읽는다(user 모듈과 같은 방식). 동시 판정은 PENDING 조건부 UPDATE 로 한 번만 반영한다.
- 2026-10-18 `.claude/scripts/test.sh`: 328개 · 실패 0개 (정정 제거 후).
- 실제 Garage: 변경한 바이트·체크섬·헤더 누락 거절, 원본 보존과 동일 내용 재전송 확인.
- V6 기존 행이 있는 PostgreSQL → V7 마이그레이션, 동시 발급 4건 → UUID 1개, 보관소 장애 503 확인.
- 샘플 페이지 JS 와 upload.sh 는 로컬 fixture 로 해시·헤더·중복 PUT 생략 확인.
- 기존 버킷은 `scripts/upload-cors.sh` 재실행이 필요하다. 실제 운영 버킷 설정은 이 작업에서 바꾸지 않았다.

후속 후보: 쓰이지 않은 PENDING 정리. Codex 가 #45 에 단 P1(판정 뒤 바꿔치기, F3)은 신규 체크섬 발급으로 막는다.

## Files

- `INDEX.md` — 이 문서. 결정과 진행 상황
- `../../docs/architecture/direct-upload.md` — 설계를 처음 보는 사람에게 설명하는 문서
