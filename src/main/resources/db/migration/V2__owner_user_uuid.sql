-- 소유자를 users.id 가 아니라 users.uuid 로 가리킨다.
--
-- 보관소 키에 소유자가 들어가는데, 그 키를 만드는 워커는 요청 맥락이 없는 비동기 실행이라
-- job 행에서 바로 읽어야 한다. id 만 두면 generation 이 user 모듈에 의존하게 된다.
--
-- 두 컬럼을 함께 두지 않는다. 조인하는 곳이 없어 bigint 가 사주는 것이 없고,
-- 인가 경로에서 정본이 둘이면 어디가 기준인지 흐려진다.
-- 자세한 근거는 GenerationJob.ownerUserUuid 의 KDoc 에 있다.
--
-- 네 단계로 나눈다. 기존 행이 있는 테이블에 NOT NULL 컬럼을 바로 붙이면 거기서 실패한다.
-- 지금 이 DB 는 비어 있어 UPDATE 가 0행이지만, 마이그레이션은 어떤 DB 에서 돌지 모르는 스크립트다.
--
-- UPDATE 는 **여기서만** 두 모듈을 잇는다. 옛 값이 users.id 였다는 사실은 이 스크립트가 알아야
-- 옮길 수 있고, 옮기고 나면 스키마에는 그 연결이 남지 않는다.
-- 짝이 없는 행이 있으면 3단계에서 시끄럽게 실패한다 — 조용히 끼워 맞추는 것보다 낫다.
--
-- `UPDATE ... FROM` 대신 상관 서브쿼리를 쓴다. 앞엣것은 PostgreSQL 문법이라 jOOQ 코드 생성이
-- H2 로 번역하다 깨진다. 이 스크립트는 스키마의 정본이자 생성기의 입력이기도 하다.

ALTER TABLE generation_jobs
    ADD COLUMN owner_user_uuid uuid;

UPDATE generation_jobs j
SET owner_user_uuid = (SELECT u.uuid FROM users u WHERE u.id = j.owner_user_id);

ALTER TABLE generation_jobs
    ALTER COLUMN owner_user_uuid SET NOT NULL;

ALTER TABLE generation_jobs
    DROP COLUMN owner_user_id;
