# Plan: 보관소에 직접 올리는 업로드 (presigned PUT)

Created: 2026-09-27
Status: active

## 무엇을 만드나

사용자가 가진 이미지를 **앱을 거치지 않고 보관소에 직접** 올리고, 그것을 i2i 입력으로 쓴다.

**완료 기준(1문장)**: 발급받은 주소로 이미지를 올리고 완료를 알리면, 그 파일을 `ImageSource.Uploaded` 로 참조해 i2i 를 돌릴 수 있다.

앱이 바이트를 받지 않는 것이 이 방식을 고른 이유 전부다. `MultipartFile` 은 힙에 통째로 올라오는데, 그건 결과물을 내려보낼 때 #35 에서 막 걷어낸 모양이다.

## 확인된 사실 (실험으로)

Garage `v2.4.1` 에 실제로 쏘아 확인했다. 추정이 아니다.

| # | 사실 | 무엇을 정했나 |
|---|---|---|
| F1 | `contentType` 이 서명에 들어간다. 다르게 올리면 **거부되고 보관소에 남지도 않는다** | 형식 선언을 발급 시점에 못박는다 |
| F2 | `contentLength` 도 서명에 들어간다 (`SignedHeaders=content-length;content-type;host`) | 크기도 발급 시점에 못박는다 |
| F3 | **같은 주소로 다시 PUT 하면 덮어써진다** (1차·2차 모두 200) | 검증 후 바꿔치기가 가능하다 |
| F4 | Garage 는 조건부 쓰기(`If-None-Match: *`)를 **지원하지 않는다** (400) | 1회용 주소로 F3 을 막을 수 없다 |
| F5 | `contentMD5` 를 서명에 넣으면 **내용이 고정된다.** 내용을 바꾸면 보관소가 400, 해시를 내용에 맞게 고치면 서명이 깨져 403 | **F3 이 무해해진다.** 같은 주소로 다시 올려도 같은 내용만 올라간다 |

> F4 를 모르고 `ifNoneMatch("*")` 를 넣었다면 **모든 업로드가 400 으로 실패**했을 것이다.
> AWS S3 는 이 기능을 지원하므로, 확인 없이 넣었으면 "AWS 에선 되고 Garage 에선 안 되는" 코드가 됐다.

## 결정 사항

| # | 결정 | 이유 |
|---|---|---|
| D1 | 발급 포트를 `PresignedUrlIssuer` 와 **따로** 둔다 | 방향이 반대이고 위험도 반대다. 내주는 주소가 새면 남이 보고, 받는 주소가 새면 남이 **쓴다**. 한 포트면 두 권한이 함께 열린다 |
| D2 | 서명에 `key` · `contentType` · `contentLength` 를 싣는다 | F1·F2. 발급 시점에 막을 수 있는 전부다 |
| D3 | **내용은 올라온 뒤에 본다** — 선두 32바이트 매직넘버 | 발급 시점에 서버는 그 바이트를 본 적이 없다. 해시를 서명에 넣어도 "말한 그 바이트"를 고정할 뿐 "그게 png 다"는 보장하지 못한다 |
| D4 | **최종 위치로 옮기지 않는다** | F5 때문이다. 내용이 서명에 묶이면 **바꿔치기가 구조적으로 불가능**해서 이동이 막던 것이 애초에 일어나지 않는다. 공유 기능이 생겨도 마찬가지다 |
| D7 | 키를 **결과물과 같은 규칙**(`users/{owner}/blobs/{digest}`)으로 둔다 | 발급 요청에 해시를 받고 그 해시를 서명에 싣는다(F5). **보관소가 "키 = 내용의 해시"를 대신 보장**하므로 우리가 전체를 읽지 않아도 된다 |
| D5 | 쓰기 수명을 읽기와 **나눈다** | F3 때문에 수명이 곧 바꿔치기 창이다. 읽기(15분)와 근거가 달라졌다. 전송이 끝날 만큼만 |
| D6 | 검증 전에는 **읽기 주소를 발급하지 않는다** | 검증을 통과하지 못한 객체는 열람 경로가 없어야 한다. `status` 의 목적이 상태 추적이 아니라 **노출 통제**다 |

### D4 는 왜 뒤집히지 않나

처음에는 "우리 제품엔 공유 경로가 없어서 피해자가 자기 자신"이라는 **제품 근거**로 미뤘다.
F5 를 확인한 뒤로는 **구조적 근거**가 됐다 — 내용이 서명에 묶여 바꿔치기 자체가 불가능하다.
공유 기능이 생겨도 이 근거는 흔들리지 않는다.

남는 것은 **"그 바이트가 정말 png 인가"** 하나뿐이고, 그것도 내용이 고정되므로 `complete` 의 검증이
**영구적으로 유효**하다. 한 번 통과하면 다시 볼 필요가 없다.

> 주의: MD5 는 충돌 공격이 알려져 있다. 다만 그 공격으로 얻는 것이 "자기 계정의 자기 파일을 바꾸는 것"이라
> 현재 위협 모델에서는 의미가 없다. `x-amz-content-sha256` 으로 올리면 그 걱정도 사라진다(미확인).

## 아직 정하지 않은 것

**U1. 발급 기록을 한 행으로 둘까, 두 행으로 둘까**

| | 안 1 `uploaded_files.status` | 안 2 `pending_uploads` + `uploaded_files` |
|---|---|---|
| `uploaded_files` 에 행이 있으면 | 쓸 수 있을 수도, 없을 수도 | **무조건 쓸 수 있다** |
| 참조하는 쪽 | `READY` 확인 필요 | 존재 확인만 → `generated` 와 **대칭** |
| 실패 이력 | 남는다 | 안 남는다 |
| 부수 효과 | — | `pending_uploads` 가 비어 있는 게 정상 → 쌓이면 이상 신호 |
| 선례 | — | GitLab `ObjectStorage::PendingDirectUpload` |

지출 기록에서 **실패한 호출도 적기로** 한 것과, 안 2 의 "실패가 안 남는다"가 어긋난다. 그게 걸리면 안 1 이거나 안 2 + 별도 이력이다.

**U3. `UploadedFile` 을 어느 모듈에 둘까**

`storage` 를 단순 어댑터가 아니라 **파일을 관리하는 팀**으로 볼 것인가.

```
generation  →  file(도메인)  →  storage(S3 어댑터)
  "이 파일로"    파일의 생애 책임    바이트를 넣고 뺀다
```

- `storage` 에 직접 두는 것은 안 된다 — 지금 그 모듈은 **DB 를 전혀 모르고**, `FileStorage` 가 "담긴 바이트가 무엇인지는 모른다" 고 선언했다
- `generation` 은 지금 유일한 소비자라 자연스럽지만, 업로드는 **생성 작업과 무관**하다
- 새 `file` 모듈이 구상과 맞다. 대가는 `@NamedInterface` 첫 사례(이 저장소엔 아직 0개)

> 이 방향으로 가면 `GeneratedFile` 도 언젠가 옮겨야 한다. 지금은 `파일 → Task` 인데 `Task → 파일` 로 뒤집히는
> 큰 리팩토링이라 **별도 과제**다. 다만 방향을 알고 새것을 놓는 것과 모르고 놓는 것은 다르다.

**U2. 키 규칙** — **D7 로 정해졌다.** 발급 때 해시를 받아 결과물과 같은 규칙을 쓴다.

## 단계와 PR

```
main
 └─ #36  feat/presigned-upload    1단계: 발급 포트 + S3 구현
     └─ #__ feat/upload-records    2단계: 테이블 + 엔티티
         └─ #__ feat/upload-api    3단계: 발급 / complete API

(별도) feat/image-to-image
         └─ 4단계: ImageSource.Uploaded 배선   ← 위 셋 + i2i 가 머지된 뒤
```

앞 PR 이 머지되면 GitHub 이 뒤 PR 의 base 를 자동으로 옮긴다.
앞에 커밋이 추가되면 뒤를 rebase 해야 하므로, **앞이 안정된 뒤에 뒤를 시작**한다.

```bash
.claude/scripts/worktree.sh feat/upload-records feat/presigned-upload
gh pr create --base feat/presigned-upload --draft
```

## Progress

- [x] **1단계** 발급 포트 + S3 구현 + 빈 등록 (#36, CI 초록)
- [ ] **1단계 보강** — `contentLength`·`contentMD5` 파라미터(F2·F5), 수명 분리(D5), 내용 고정 테스트(F5), KDoc 에 "왜 형식을 묶나"
- [ ] **2단계** U1·U2 결정 → `V5__uploaded_files.sql` + 엔티티 + repository
- [ ] **3단계** `POST /api/uploads`(발급) · `POST /api/uploads/{uuid}/complete`(검증) + 기동 로그
- [ ] **4단계** `ImageSource.Uploaded` + `ImageSourceFinder` 가지
- [ ] (선택) 누락 복구 폴링 — complete 을 못 부른 발급 정리

## 단계별 체크리스트

### 1단계 보강
- `issueUpload(key, contentType, contentLength)` — 크기를 서명에
- 수명을 `presignedUploadTtl` 로 분리 (D5). 읽기 15분과 근거가 다르다
- 테스트 `같은 주소로 다시 올리면 덮어쓴다` — F3 을 코드에 고정. **2·3단계에서 status 가 왜 필요한지의 근거**가 된다
- KDoc: 저장된 `Content-Type` 이 GET 응답 헤더가 되고 `inline` 으로 내려가므로, 통제하지 않으면 보관소 도메인에서 임의 HTML 이 렌더링된다

### 2단계
- U1 을 정하고 그에 맞춰 테이블 1개 또는 2개
- **엔티티와 마이그레이션은 같은 커밋** — 짝일 때만 `SchemaMigrationTest` 가 검증한다. 테이블만 넣으면 아무도 안 본다
- **V3·V4 는 지출 기록이 가져갔다 → `V5`**
- 소유자는 `owner_user_uuid uuid` (`V2` 규약)
- 선례: `GeneratedFile.kt` · `V1__baseline_schema.sql` · `V2__owner_user_uuid.sql`

### 3단계
- 발급: `contentType` 화이트리스트(OpenAI 가 받는 png·jpeg·webp 와 **정확히 같게**) + `byteSize` 필수
- complete: `HeadObject` 로 크기 대조 → 선두 32바이트 Range GET → 매직넘버 대조 → 불일치면 **객체 삭제**
- `ImageContentType.detect()` 신설 — PNG `89 50 4E 47 0D 0A 1A 0A` · JPEG `FF D8 FF` · WebP `RIFF....WEBP`
- **전체 디코드를 하지 않는다** — 픽셀 수가 수억인 이미지를 `ImageIO.read` 로 열면 메모리가 터진다
- 예외 매핑은 `GenerationApiExceptionHandler` 의 `assignableTypes` 에 추가 (새 advice 금지)
- 기동 로그(`describeStorage`)에 업로드 발급 가능 여부 추가

### 4단계
- `ImageSource` 에 `Uploaded` + `@JsonSubTypes` 한 줄
- `ImageSourceFinder` 의 `when` 에 가지 하나 — sealed 라 빠뜨리면 컴파일 에러

## 함정

- **`ByteArrayResource.getFilename()` 은 기본이 null** — override 하지 않으면 파트가 파일로 안 나가고, 증상은 저쪽의 400 이라 원인이 멀다
- **jOOQ 가 마이그레이션 SQL 을 H2 파서로 읽는다** — 인덱스 `DESC` 금지, 복합 `ALTER` 금지
- **`spring.servlet.multipart` 설정은 이 경로에 필요 없다** — 앱이 파일을 받지 않는다
- **`contentLength` 를 서명에 넣으면 스트리밍 업로드가 불가능해진다** — 올리는 쪽이 정확한 크기를 미리 알아야 한다. 브라우저는 `File.size` 로 안다
- **크기·형식·키를 다 고정해도 같은 크기의 다른 파일로 덮어쓸 수 있다**(F3). 그래서 D6 이 필요하다

## Resume Point

**U1(한 행 vs 두 행)을 정하는 것부터.** 정해지면 2단계에서 만들 파일이 확정된다.
1단계 보강은 U1 과 독립이라 병행 가능하다 — `storage` 모듈이고 2단계는 `generation` 이라 파일이 겹치지 않는다.

## Files

- `INDEX.md` — 이 문서. 결정과 진행 상황
- `step-2-records.md` — **2단계 작업 카드.** 손이 움직일 때 보는 것
- `../../docs/architecture/direct-upload.md` — 설계를 처음 보는 사람에게 설명하는 문서
