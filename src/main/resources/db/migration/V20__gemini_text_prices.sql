-- Gemini 텍스트 모델의 첫 단가. 1M 토큰당 USD. 한 줄이 한 모델의 가격표 한 장이다.
--
-- 단가를 정한 2026-11-08 부터 적용한다. 텍스트 입력 단가다 — 이미지 · 오디오 입력은 받지 않는다.
--
-- generateContent 의 usageMetadata 는 promptTokenCount · candidatesTokenCount · thoughtsTokenCount 다.
-- 출력 단가에 thinking 토큰이 들어 있어("Output price (including thinking tokens)") 답과 생각에 같은 단가를 매긴다.
--
-- 두 출력 칸은 0 이면 응답에서 빠진다. 생각하지 않은 호출엔 thoughtsTokenCount 가, 막힌 호출엔 candidatesTokenCount 가 없다.
-- 그래서 zeroIfAbsent 로 고른다. promptTokenCount 는 늘 와서 엄격하게 둔다.
--
-- Free Tier 키로 부른 호출도 이 단가로 잡힌다. 이 서비스 관점에선 토큰을 쓴 것이 맞다.
--
-- 암묵 캐시 할인은 넣지 않는다. cachedContentTokenCount 는 promptTokenCount 의 일부라 할인을 매기려면 음수 계수가
-- 필요하다(V13 과 같은 이유). 같은 instructions · 스키마를 되풀이할수록 실제보다 비싸게 잡힌다.
--
-- gemini-3.6-flash 는 2027-01-01 부터 단가가 두 배다. 발표된 변경이라 처음부터 두 장으로 넣는다.
-- 경계는 UTC 자정이다 — 가격표가 시간대를 밝히지 않는다.
INSERT INTO model_price_books (provider, model, effective_from, effective_to, source_url, rates)
VALUES ('gemini', 'gemini-3.6-flash',
        TIMESTAMP WITH TIME ZONE '2026-11-08 00:00:00+00', TIMESTAMP WITH TIME ZONE '2027-01-01 00:00:00+00',
        'https://aistudio.google.com/docs/pricing',
        '{"usdPerMillion": {"promptTokenCount": 0.75, "candidatesTokenCount": 3.75, "thoughtsTokenCount": 3.75},
          "zeroIfAbsent": ["candidatesTokenCount", "thoughtsTokenCount"]}'),
       ('gemini', 'gemini-3.6-flash',
        TIMESTAMP WITH TIME ZONE '2027-01-01 00:00:00+00', NULL,
        'https://aistudio.google.com/docs/pricing',
        '{"usdPerMillion": {"promptTokenCount": 1.50, "candidatesTokenCount": 7.50, "thoughtsTokenCount": 7.50},
          "zeroIfAbsent": ["candidatesTokenCount", "thoughtsTokenCount"]}'),
       ('gemini', 'gemini-3.5-flash-lite',
        TIMESTAMP WITH TIME ZONE '2026-11-08 00:00:00+00', NULL,
        'https://aistudio.google.com/docs/pricing',
        '{"usdPerMillion": {"promptTokenCount": 0.30, "candidatesTokenCount": 2.50, "thoughtsTokenCount": 2.50},
          "zeroIfAbsent": ["candidatesTokenCount", "thoughtsTokenCount"]}');
