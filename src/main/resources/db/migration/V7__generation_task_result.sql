-- 파일을 만들지 않는 AI 기능의 결과는 Task 에 JSON으로 보관한다.
-- 기존 이미지 Task 는 파일 행을 계속 정본으로 삼으므로 이 칸은 비어 있다.
ALTER TABLE generation_job_tasks
    ADD COLUMN result jsonb;
