-- OpenAI 텍스트 모델의 첫 단가. 1M 토큰당 USD. 한 줄이 한 모델의 가격표 한 장이다.
--
-- V10 과 같이 단가를 정한 2026-10-18 부터 적용한다.
--
-- Responses API 의 usage 는 input_tokens · output_tokens 다. 추론 토큰은 output_tokens 안에 들어 있어
-- (output_tokens_details.reasoning_tokens 는 그 내역이다) 따로 매기지 않는다.
--
-- 캐시 입력 할인은 넣지 않는다. cached_tokens 는 input_tokens 의 일부라 할인을 매기려면 음수 계수가
-- 필요한데 PriceRates 는 음수 단가를 받지 않는다. 그래서 캐시가 걸린 호출은 실제보다 비싸게 잡힌다 —
-- 같은 instructions · 스키마를 되풀이할수록 차이가 커진다.
--
-- 가격표의 272K 이하 컨텍스트 단가다. 그보다 긴 입력의 단가는 담지 않는다.
INSERT INTO model_price_books (provider, model, effective_from, source_url, rates)
VALUES ('openai', 'gpt-6-astra', TIMESTAMP WITH TIME ZONE '2026-10-18 00:00:00+00', 'https://developers.openai.com/api/docs/pricing',
        '{"usdPerMillion": {"input_tokens": 10, "output_tokens": 50}}'),
       ('openai', 'gpt-6.1-sol', TIMESTAMP WITH TIME ZONE '2026-10-18 00:00:00+00', 'https://developers.openai.com/api/docs/pricing',
        '{"usdPerMillion": {"input_tokens": 2, "output_tokens": 10}}'),
       ('openai', 'gpt-6-luna', TIMESTAMP WITH TIME ZONE '2026-10-18 00:00:00+00', 'https://developers.openai.com/api/docs/pricing',
        '{"usdPerMillion": {"input_tokens": 0.10, "output_tokens": 0.50}}'),
       ('openai', 'gpt-5.6-terra', TIMESTAMP WITH TIME ZONE '2026-10-18 00:00:00+00', 'https://developers.openai.com/api/docs/pricing',
        '{"usdPerMillion": {"input_tokens": 2, "output_tokens": 12}}');
