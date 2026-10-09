# convention-gate

저장소의 `.claude/conventions.json` 규약을 **AI 가 Kotlin 파일을 쓰는 순간**에 건다.
규약 파일이 없는 저장소에서는 아무것도 하지 않는다.

| 언제 | 무엇을 |
|---|---|
| Write 로 **새 파일**을 만들 때 | 쓰기 전에 검사하고, 어기면 쓰지 않는다. 허용되는 역할과 그 뜻을 함께 알려 준다 |
| Write 로 덮기 · Edit · Bash 뒤 | 바뀐 파일을 검사하고, 어기면 모델에게만 알린다. 파일이 그대로면 같은 위반을 거듭 알리지 않는다 |

Bash 뒤에는 도구가 아니라 **결과로 바뀐 파일**(`git status`)을 본다. 이름 바꾸기는 대개 `git mv`·`perl` 로 하기 때문이다.
명령에 나온 절대 경로의 저장소도 함께 보므로 `cd /다른/워크트리 && …` 도 놓치지 않는다.

같은 규약을 저장소의 `ArchitectureTest` 가 컴파일된 클래스에 건다. 여기는 컴파일 없이 소스 텍스트를 읽는다.
규칙의 문장·이유·예는 규약 파일에 있고, 이 Mod 와 테스트는 검사 종류마다 **방법만** 안다.

## 검사 종류

규칙마다 `check.kind` 로 아래 여섯 가지 중 하나를 고른다. 어느 층에 어떤 이름으로 걸지는 규칙이 정하고,
이 저장소에 지금 걸린 규칙은 `.claude/conventions.json` 의 `rules` 에 있다. 예는 그 규칙들로 걸리는 모양이다.

| `kind` | 보는 곳 | 걸리는 예 |
|---|---|---|
| `no-import` | `from` 층 파일의 import 중 `to` 층을 가리키는 것 | `application/TaskWorker.kt` 가 `…generation.infrastructure.JpaGenerationJobRepository` 를 import |
| `file-name` | `in` 층의 파일 이름. `endsWith` 로 끝나거나 `startsWith` 로 시작해야 한다. `except` 의 경로는 뺀다 | `application/FooManager.kt` |
| `banned-suffix` | 층과 상관없이 선언된 타입 이름과 파일 이름 | `GenerationJobRepositoryImpl` |
| `interface-only` | `in` 층에서 `endsWith` 로 끝나는 선언이 interface 인지 | `domain/` 의 `class GenerationJobRepository` |
| `port-impl-name` | `implIn` 층 선언의 상위 타입 중 `portsIn` 층에서 import 한 포트. 이름이 `{prefixes}{포트}` 여야 한다 | `GenerationJobJpaRepository : GenerationJobRepository` |
| `controller-deps` | `in` 층의 `controllerEndsWith` class 가 주 생성자로 받는 `typesFrom` 층 타입. `allowedEndsWith` 로 끝나야 한다 | `GenerationJobController(private val writer: TaskStateWriter)` |

층은 기준 패키지 아래 `<모듈>/<층>/…` 의 둘째 칸으로 읽는다. 모듈 루트의 파일(공개 포트·값)은 층이 없어서
층을 묻는 검사에는 걸리지 않고, 그 파일을 가리키는 import 도 `no-import` 에 걸리지 않는다.

모르는 `kind` 는 넘어가지 않고 모든 파일에서 위반으로 알린다. 새 종류는 이 Mod 와 `ArchitectureTest` 에 검사 방법을 함께 더한다.

## 예

AI 가 `generation/application/UsageReader.kt` 를 새로 만들려 하면 쓰기 전에 막히고, 모델은 이렇게 받는다.
한 파일이 여러 규칙에 걸리면 규칙마다 한 덩이씩 나온다.

```
[convention-gate] generation/application/UsageReader.kt 이 .claude/conventions.json 의 규약을 어긴다.
■ application-roles — application 의 파일 이름은 정해진 역할로 끝나거나 Default 로 시작한다
  위반: UsageReader.kt
  이유: 이름만 보고 컨트롤러 진입점인지 그 아래 부품인지 알게 한다. 목록에 없는 역할은 지어내지 말고 사람에게 묻는다
  허용되는 끝말: Service(컨트롤러가 부르는 진입점. 애그리거트당 하나), Writer(저장과 트랜잭션만 맡는 부품), …
  허용되는 앞말: Default(모듈 밖에 공개한 포트의 기본 구현)
■ banned-suffixes — 다음 끝말로 타입 이름을 짓지 않는다
  위반: UsageReader — Reader: Writer 와 짝 이름이라 데이터 접근 부품으로 읽힌다. 조회 진입점은 Service 다
  이유: 각 끝말이 실제와 다른 층이나 역할을 가리킨다
  예: GenerationJobService, JpaGenerationJobRepository
규칙에 없는 역할이면 이름을 짓지 말고 사용자에게 묻는다. 정해지면 .claude/conventions.json 에 이유와 함께 추가한다.
```

이미 있는 파일을 Edit 로 고쳐 컨트롤러가 부품을 받게 되면, 고친 것은 그대로 두고 도구 결과 뒤에 이렇게 붙는다.

```
[convention-gate] generation/api/GenerationJobController.kt 이 .claude/conventions.json 의 규약을 어긴다.
■ controllers-call-services — 컨트롤러가 생성자로 받는 application 타입은 Service 뿐이다
  위반: GenerationJobController 가 TaskStateWriter 를 받는다
  이유: Controller → Service → Repository 로 층이 이름에 드러나게 한다. Writer 같은 부품은 Service 를 거쳐 쓴다
  예: class GenerationJobController(private val generationJobService: GenerationJobService)
규칙에 없는 역할이면 이름을 짓지 말고 사용자에게 묻는다. 정해지면 .claude/conventions.json 에 이유와 함께 추가한다.
```

## 설치

```
/plugin install convention-gate --marketplace youngsu5582/personal-ai-workbench
```

main 에 들어가기 전이나 이 폴더를 고치는 중에는 `claude --plugin-dir .claude/mods/convention-gate` 로 띄운다.

## 테스트

```
claude plugin validate .claude/mods/convention-gate
claude plugin test .claude/mods/convention-gate
```

## 한계

- Claude Code 의 도구 호출만 본다. IDE 에서 사람이 쓴 코드는 `ArchitectureTest` 가 잡는다.
- 소스를 텍스트로 읽는다. import 없이 완전한 이름으로 쓴 타입, 한 줄에 여러 선언처럼 드문 모양은 놓칠 수 있다.
- 검사기가 예외를 던지면 그 호출은 그냥 통과시킨다. 규약 검사가 고장 났다고 작업을 막지 않는다.
