# 2단계 — 올라온 파일을 기록한다

`1단계 발급` → **`2단계 기록`** → `3단계 API` → `4단계 i2i 연결`

지금 있는 것: 주소를 발급하는 코드뿐. **발급했다는 사실도, 올라왔다는 사실도 어디에도 안 남는다.**

---

## 0. 시작

```bash
.claude/scripts/worktree.sh feat/upload-records feat/presigned-upload
cd ../upload-records
```

---

## 1. 먼저 정한다 — 이거 정하면 나머지가 확정된다

### U1. 한 행이냐 두 행이냐

```
발급 시점              완료 시점
"이 키에 올려도 돼"  →  "올라왔고 검증됐다"
```

이 둘을 **한 행으로** 볼 것인가, **두 행으로** 볼 것인가.

| | 안 1 — `uploaded_files.status` | 안 2 — `pending_uploads` + `uploaded_files` |
|---|---|---|
| 테이블 | 1개 | 2개 |
| `uploaded_files` 에 행이 있으면 | 쓸 수 있을지 **확인해야** 안다 | **무조건 쓸 수 있다** |
| 4단계에서 참조할 때 | `READY` 인지 매번 확인 | 존재 확인만 (generated 와 같은 모양) |
| 실패한 업로드 | 남는다 | 안 남는다 |
| 전환 | `UPDATE` | `INSERT` + `DELETE` |

> 걸리는 점: 지출 기록(`provider_calls`)에서 **실패한 호출도 적기로** 했는데, 안 2 는 실패가 안 남는다. 일관성이 신경 쓰이면 안 1 이거나, 안 2 + 이력 테이블.

### U2. 키 규칙

올리기 **전에** 정해야 해서 내용을 모른다. → 내용 주소(digest)를 쓸 수 없다.

```
uploads/{ownerUuid}/{fileUuid}.{ext}
```

결과물(`users/{owner}/blobs/{digest}`)과 규칙이 달라지는데, 그걸 받아들일지가 판단.

---

## 2. 만든다

### `src/main/resources/db/migration/V5__uploaded_files.sql`

- **V3·V4 는 지출 기록이 가져갔다.** V5 다
- 베낄 것: `V1__baseline_schema.sql` (주석 톤, 제약 이름 `uk_`·`ck_`·`idx_`)
- 소유자는 `owner_user_uuid uuid` — `V2__owner_user_uuid.sql` 이 정한 규약
- ⚠️ **jOOQ 가 이 SQL 을 H2 파서로 읽는다** → 인덱스에 `DESC` 금지, `ALTER` 를 한 문장에 여러 개 금지

### `generation/domain/UploadedFile.kt`

- 베낄 것: `GeneratedFile.kt` (엔티티 모양, `@JdbcTypeCode(SqlTypes.JSON)`, uuid 컬럼 규약)
- `status` 를 enum 으로 둔다면 ⚠️ **`@get:JsonValue`** 로 표기 고정 — 안 붙이면 상수 이름이 DB 에 박혀서 리네임하는 순간 옛 행을 못 읽는다

### `generation/infrastructure/UploadedFileRepository.kt`

- 베낄 것: `GeneratedFileRepository.kt`
- `generated_files` 와 달리 **소유자를 직접 갖는다** → 조인이 없으므로 jOOQ 말고 **JPA 파생 쿼리로 충분** (`findByUuidAndOwnerUserUuid`)

---

## 3. 확인한다

```bash
.claude/scripts/test.sh
```

`SchemaMigrationTest` 가 Testcontainers 로 진짜 PostgreSQL 에 걸어 **엔티티와 스키마를 대조**한다.
단언문이 없다 — **뜨면 맞는 것**이다.

⚠️ **엔티티와 마이그레이션은 같은 커밋에.** 짝일 때만 이 테스트가 검증한다. 테이블만 넣으면 아무도 안 본다.

---

## 4. 커밋하고 올린다

```bash
git add src/
# feat: 올라온 파일을 기록한다   ← 본문은 "왜" 3~4항목
git push -u origin feat/upload-records
gh pr create --base feat/presigned-upload --draft
```

base 가 **`feat/presigned-upload`** 다 (main 아님). 1단계가 머지되면 GitHub 이 base 를 main 으로 옮겨준다.

---

## 막히면

| 증상 | 원인 |
|---|---|
| 기동 시 `Schema-validation: missing table` | 마이그레이션은 넣었는데 엔티티 매핑이 다르다 |
| jOOQ 코드 생성 실패 | H2 파서가 못 읽는 SQL (`DESC`, 복합 `ALTER`) |
| `SchemaMigrationTest` 만 실패 | 로컬 H2 와 진짜 PostgreSQL 의 타입 차이 (`jsonb`, `uuid`) |
| Docker 없음 | Testcontainers 가 못 뜬다 |

---

## 다음

3단계는 내가 쓴다 — `POST /api/uploads`(발급) · `POST /api/uploads/{uuid}/complete`(검증).
**2단계에서 정한 U1 이 complete 의 계약을 정한다.**
