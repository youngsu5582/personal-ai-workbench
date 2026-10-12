# AGENTS.md

이 저장소의 코딩 관례는 `CLAUDE.md` 의 "코딩 관례" 절에 있다. 리뷰할 때 그 절을 기준으로 삼는다.
이름과 자리 규칙(`.claude/conventions.json`)은 `ArchitectureTest` 가 CI 에서 막으므로 리뷰에서 되풀이하지 않는다.

## Code Review Rules

리뷰 코멘트는 한국어로 쓴다.

### 설명되지 않은 변경

PR 본문과 커밋 메시지가 설명하지 않는 변경이 diff 에 있으면 짚는다.
동작이 바뀌는 줄(설정값, 호출하는 API, 의존성)이면 P1 이다.

이 저장소는 AI 와 함께 작업하므로, 의도하지 않은 변경이 다른 수정에 섞여 커밋될 수 있다.

### 적용된 마이그레이션

`src/main/resources/db/migration` 에서 이미 main 에 있는 `V*__*.sql` 이 수정되면 P1 이다.
적용된 마이그레이션이 바뀌면 Flyway 의 체크섬 검증이 실패해 기존 DB 에서 기동하지 못한다. 변경은 새 버전 파일로 한다.

기존 행이 있을 수 있는 테이블에 NOT NULL 컬럼을 더하면서 기본값이나 기존 행을 채우는 단계가 없으면 P1 이다.
`SchemaMigrationTest` 는 빈 DB 에서 돌므로 이 실패를 잡지 못한다.
