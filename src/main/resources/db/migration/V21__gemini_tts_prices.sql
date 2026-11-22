-- Gemini 음성 모델의 첫 단가. 1M 토큰당 USD. 한 줄이 한 모델의 가격표 한 장이다.
--
-- 단가를 정한 2026-11-22 부터 적용한다. 입력은 글, 출력은 소리다. 소리는 초당 25 토큰이라 출력 단가가 곧 시간 단가다.
--
-- Interactions 의 usage 는 generateContent 의 usageMetadata 와 칸 이름이 다르다 —
-- total_input_tokens · total_output_tokens 다. 두 칸 모두 늘 와서 zeroIfAbsent 를 두지 않는다.
-- total_thought_tokens 도 오지만 음성 모델에서는 0 이고, 가격표의 출력 칸은 생각 토큰을 말하지 않아 값을 매기지 않는다.
--
-- Free Tier 키로 부른 호출도 이 단가로 잡힌다(V20 과 같다). 이 서비스 관점에선 토큰을 쓴 것이 맞다.
--
-- 두 모델 모두 2027-01-01 부터 단가가 두 배다. 발표된 변경이라 처음부터 두 장으로 넣는다.
-- 경계는 UTC 자정이다 — 가격표가 시간대를 밝히지 않는다.
INSERT INTO model_price_books (provider, model, effective_from, effective_to, source_url, rates)
VALUES ('gemini', 'gemini-3.8-flash-tts',
        TIMESTAMP WITH TIME ZONE '2026-11-22 00:00:00+00', TIMESTAMP WITH TIME ZONE '2027-01-01 00:00:00+00',
        'https://aistudio.google.com/docs/pricing',
        '{"usdPerMillion": {"total_input_tokens": 0.50, "total_output_tokens": 9.00}}'),
       ('gemini', 'gemini-3.8-flash-tts',
        TIMESTAMP WITH TIME ZONE '2027-01-01 00:00:00+00', NULL,
        'https://aistudio.google.com/docs/pricing',
        '{"usdPerMillion": {"total_input_tokens": 1.00, "total_output_tokens": 18.00}}'),
       ('gemini', 'gemini-3.8-flash-lite-tts',
        TIMESTAMP WITH TIME ZONE '2026-11-22 00:00:00+00', TIMESTAMP WITH TIME ZONE '2027-01-01 00:00:00+00',
        'https://aistudio.google.com/docs/pricing',
        '{"usdPerMillion": {"total_input_tokens": 0.50, "total_output_tokens": 6.00}}'),
       ('gemini', 'gemini-3.8-flash-lite-tts',
        TIMESTAMP WITH TIME ZONE '2027-01-01 00:00:00+00', NULL,
        'https://aistudio.google.com/docs/pricing',
        '{"usdPerMillion": {"total_input_tokens": 1.00, "total_output_tokens": 12.00}}');
