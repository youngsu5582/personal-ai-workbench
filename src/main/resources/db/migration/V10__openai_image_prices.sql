-- OpenAI 이미지 모델의 첫 단가. 1M 토큰당 USD. 한 줄이 한 모델의 가격표 한 장이다.
--
-- 가격표가 시작일을 밝히지 않아 단가를 정한 2026-10-18 부터 적용한다. 그 전의 호출은 비용을 모르는 채로 남는다.
--
-- 캐시 입력 단가는 넣지 않는다. Responses API 에만 적용되고, 우리는 Images API 를 직접 부른다.
-- 입력 이미지 토큰은 edits 에서만 생긴다.
--
-- output_tokens 전체에 이미지 출력 단가를 매긴다. 이 모델들에는 텍스트 출력 단가가 없다.
-- 텍스트 출력 단가가 있는 모델(gpt-image-1.5)을 들이면 그 표는
-- output_tokens_details.image_tokens 와 output_tokens_details.text_tokens 에 따로 매긴다.
--
-- gpt-image-2 · 2.5-sunburst · 2.5-flare 는 토큰 단가가 같다. 장당 비용이 다른 것은 같은 품질 · 크기에서도
-- 쓰는 토큰 수가 달라서고, 응답의 토큰 수를 곱하므로 그 차이는 단가표가 아니라 usage 가 가져온다.
INSERT INTO model_price_books (provider, model, effective_from, source_url, rates)
VALUES ('openai', 'gpt-image-1', TIMESTAMP WITH TIME ZONE '2026-10-18 00:00:00+00', 'https://developers.openai.com/api/docs/pricing',
        '{"usdPerMillion": {"input_tokens_details.text_tokens": 5, "input_tokens_details.image_tokens": 10, "output_tokens": 40}}'),
       ('openai', 'gpt-image-2', TIMESTAMP WITH TIME ZONE '2026-10-18 00:00:00+00', 'https://developers.openai.com/api/docs/pricing',
        '{"usdPerMillion": {"input_tokens_details.text_tokens": 5, "input_tokens_details.image_tokens": 8, "output_tokens": 30}}'),
       ('openai', 'gpt-image-2.5-sunburst', TIMESTAMP WITH TIME ZONE '2026-10-18 00:00:00+00', 'https://developers.openai.com/api/docs/pricing',
        '{"usdPerMillion": {"input_tokens_details.text_tokens": 5, "input_tokens_details.image_tokens": 8, "output_tokens": 30}}'),
       ('openai', 'gpt-image-2.5-flare', TIMESTAMP WITH TIME ZONE '2026-10-18 00:00:00+00', 'https://developers.openai.com/api/docs/pricing',
        '{"usdPerMillion": {"input_tokens_details.text_tokens": 5, "input_tokens_details.image_tokens": 8, "output_tokens": 30}}');
