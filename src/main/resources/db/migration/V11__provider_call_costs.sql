-- 호출의 비용을 원장에서 떼어 낸다. 근거는 docs/adr/0003-cost-separate-from-ledger.md 에 있다.
--
-- 토큰 수는 Provider 가 정한 사실이고 바뀌지 않는다. 비용은 그 사실에 우리 단가표를 곱한 해석이고,
-- 표가 고쳐지면 다시 계산된다. 한 행에 두면 불변인 원장에 바뀌는 칸이 생긴다.
-- 이제 provider_calls 는 INSERT 만 받는다.
--
-- 행이 있으면 폴러가 본 호출이다. 봤지만 비용을 정하지 못하면(맞는 표 없음 · 사용량 없음) usd 가 NULL 인 행을 남긴다 —
-- 비용을 원장 칸에 두면 "아직 안 봤다" 와 "봤지만 모른다" 가 똑같이 NULL 이라 폴러가 매번 다시 집는다.
--
-- 제약을 건다. provider_calls 가 제약을 두지 않는 이유는 그 INSERT 가 돈을 쓴 뒤에 일어나서인데,
-- 이 INSERT 는 실패해도 다음 주기에 다시 하면 된다.
CREATE TABLE provider_call_costs (
    -- 호출 하나에 비용 하나. 두 번 계산해도 두 번째 INSERT 가 여기서 막힌다.
    provider_call_id bigint                      NOT NULL,

    -- NULL 이 곧 "봤지만 모른다" 다. UNKNOWN 값을 따로 두지 않는다.
    -- 반올림하지 않는다 — 호출당 $0.00019 를 센트로 접으면 0 이 되고 백 건을 더해도 0 이다.
    usd              numeric(18, 8),
    -- 라벨이 아니라 재계산의 필터다. REPORTED 행을 우리 추정으로 덮지 않기 위한 것.
    source           varchar(32),
    -- 어느 표로 계산했나. Provider 가 달러로 답한 REPORTED 에는 없다.
    price_book_id    bigint,
    priced_at        timestamp(6) with time zone NOT NULL,

    CONSTRAINT pk_provider_call_costs PRIMARY KEY (provider_call_id),
    CONSTRAINT fk_provider_call_costs_call FOREIGN KEY (provider_call_id) REFERENCES provider_calls (id),
    CONSTRAINT fk_provider_call_costs_book FOREIGN KEY (price_book_id) REFERENCES model_price_books (id),
    -- 금액과 출처는 함께 정해진다. 하나만 있으면 재계산이 REPORTED 를 가려내지 못한다.
    CONSTRAINT ck_provider_call_costs_pair CHECK ((usd IS NULL AND source IS NULL) OR (usd IS NOT NULL AND source IS NOT NULL)),
    -- 값이 늘면 이 제약도 같이 고쳐야 한다. ddl-auto 는 이것을 갱신하지 못한다.
    CONSTRAINT ck_provider_call_costs_source CHECK (source IN ('REPORTED', 'DERIVED', 'ESTIMATED'))
);

-- 운영 배포 전이라 옮길 행이 없다. 개발 DB 에 남은 값은 버린다 — 단가표 없이 채워진 것은 REPORTED 뿐이고,
-- 그 원문은 reported_amount 에 그대로 있어 폴러가 다시 옮긴다.
ALTER TABLE provider_calls DROP CONSTRAINT ck_provider_calls_basis;
ALTER TABLE provider_calls DROP COLUMN cost_usd;
ALTER TABLE provider_calls DROP COLUMN cost_basis;
ALTER TABLE provider_calls DROP COLUMN price_book_version;
