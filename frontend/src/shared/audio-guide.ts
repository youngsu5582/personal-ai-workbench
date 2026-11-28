/**
 * TTS · STT 를 고를 때 콘솔이 보이는 안내. 서버는 목소리의 이름만 주고 어떤 소리인지는 주지 않는다.
 *
 * 목소리 특징은 Gemini 음성 생성 문서의 목소리 표를 한 단어로 옮긴 것이다. 표에 없는 목소리는 이름만 보인다.
 * 고르는 요령은 `docs/audio-guide.md` 에 있다.
 */
export const VOICE_TRAITS: Readonly<Record<string, string>> = {
  Zephyr: "밝음", Puck: "경쾌함", Charon: "설명조", Kore: "단단함", Fenrir: "들뜸",
  Leda: "앳됨", Orus: "단단함", Aoede: "산뜻함", Callirrhoe: "느긋함", Autonoe: "밝음",
  Enceladus: "숨소리 섞임", Iapetus: "또렷함", Umbriel: "느긋함", Algieba: "매끄러움", Despina: "매끄러움",
  Erinome: "또렷함", Algenib: "걸걸함", Rasalgethi: "설명조", Laomedeia: "경쾌함", Achernar: "부드러움",
  Alnilam: "단단함", Schedar: "고른 톤", Gacrux: "성숙함", Pulcherrima: "적극적", Achird: "친근함",
  Zubenelgenubi: "편안함", Vindemiatrix: "온화함", Sadachbia: "활기참", Sadaltager: "박식함", Sulafat: "따뜻함",
};

/**
 * 말투 예시. 짧게, 이번에 **어떻게 읽을지**(감정 · 속도 · 크기)만 적는다.
 * 나이 · 성별 · 억양처럼 소리 자체의 성격은 목소리가 정한다 — 말투에 적으면 목소리와 부딪힌다.
 */
export const STYLE_EXAMPLES = ["차분하고 또렷하게", "천천히 또박또박", "밝고 경쾌하게", "속삭이듯 작게", "따뜻하고 다정하게"] as const;

export const STYLE_HINT = "비워 두고 먼저 들어 보세요. 나이 · 성별 · 억양은 말투가 아니라 목소리로 골라요.";

/** 오디오를 첨부한 글 생성(STT)의 프롬프트 예시. 받아쓰기 말고도 들은 것을 바로 정리 · 평가하게 할 수 있다. */
export const STT_PROMPT_EXAMPLES = ["들리는 그대로 받아 써 줘", "받아 쓰고 핵심을 세 줄로 정리해 줘", "말의 속도와 말투를 평가해 줘"] as const;

/** 모델을 고를 때 알아 둘 것. 확인 상태와 단가 차이는 서버의 `GeminiAudioModel` 과 같게 둔다. */
const MODEL_NOTES: Readonly<Record<string, string>> = {
  "gemini-3.8-flash-tts": "기본 모델이에요. 실제 호출로 확인했어요.",
  "gemini-3.8-flash-lite-tts": "출력 단가가 flash-tts 의 2/3 예요. 아직 실제로 불러 확인하지 않았어요.",
};

export const audioModelNote = (model: string): string | undefined => MODEL_NOTES[model];
