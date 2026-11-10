-- Task 가 마지막으로 집힌 시각과 끝난 시각.
--
-- 접수 시각만으로는 "이 Task 는 왜 느렸나" 를 가를 수 없다. 셋이 있으면 Task 마다
-- 대기(started_at - created_at) 와 처리(finished_at - started_at) 가 나온다.
-- RUNNING 에 머문 채 남은 Task 를 회수할 때도 "언제 집혔나" 가 근거다.
--
-- started_at 은 재시도로 다시 집히면 바뀐다 — 마지막 시도의 시작이다.
-- finished_at 은 다시 큐로 돌아가면 지운다. 끝난 것이 아니다.
--
-- nullable 이다. 아직 집히지 않았거나 끝나지 않은 Task 가 정상이다.
-- 기존 행도 채우지 않는다 — 그때는 재지 않았으므로 지어낼 것이 없다.
ALTER TABLE generation_job_tasks
    ADD COLUMN started_at  timestamp(6) with time zone,
    ADD COLUMN finished_at timestamp(6) with time zone;
