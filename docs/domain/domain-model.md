# Personal AI Workbench 도메인 모델

## 1. 문제 영역

Personal AI Workbench는 외부 AI Provider의 이미지 생성 작업을 개인 소유의 비동기 Job으로 관리한다.

핵심 흐름은 다음과 같다.

```text
생성 요청
→ GenerationJob 생성
→ Provider 작업 제출
→ 상태 동기화
→ 결과 파일 저장
→ Job과 결과 파일 조회
```

이 시스템의 핵심은 Provider API를 그대로 노출하는 것이 아니라, 외부 작업을 내부 도메인 모델과 운영 가능한 상태 흐름으로 관리하는 데 있다.

## 2. Bounded Context

현재 MVP는 하나의 Bounded Context로 시작한다.

```text
AI Generation Workbench
```

외부 Provider의 모델명·상태값·응답 payload는 이 경계 안의 Provider adapter에서만 다룬다. API와 도메인 계층은 Provider별 표현을 직접 알지 않는다.

## 3. 도메인 규칙과 불변식

### User / UserIdentity

- 외부 인증 주체의 식별 기준은 `provider` 같은 논리 이름이 아니라 **(issuer, subject) 쌍**이다.
- `UNIQUE(issuer, subject)` — 같은 외부 계정이 두 User에 연결될 수 없다.
- `issuer`는 저장 전에 정규화한다. Google은 `iss`를 `https://accounts.google.com`과
  `accounts.google.com` 두 형태로 줄 수 있고, 정규화하지 않으면 같은 사람이 두 행으로 갈라진다.
- `subject`는 해당 발급자 안에서 불변인 값만 쓴다. GitHub의 `login`처럼 변경 가능한 값은 쓰지 않는다.
- **로그인 수단이 0개인 User는 존재할 수 없다.** 마지막 identity는 해제할 수 없다.
  이 규칙은 `UserWriter.unlink`가 지킨다. "자식 행이 최소 1개"를 강제하는 DB 제약은 없기 때문이다.
  현재 count 확인은 TOCTOU에 열려 있다 — 해제 UI가 생기는 시점에 `@Version` 또는 비관적 락이 필요하다.
- 한 User에 같은 issuer를 두 번 연결하지 않는다 (구글 계정 두 개를 한 계정에 붙일 수 없다).
- **이메일이 같다는 이유로 기존 User에 자동 연결하지 않는다.** 공격자가 내 이메일로 다른 provider에
  계정을 만들면 내 계정에 들어오게 된다. 기존 계정에 붙이는 것은 로그인된 상태에서만 가능하다.
- `email`은 User가 아니라 identity가 주장하는 값이므로 `UserIdentity`에 둔다. 로그인 식별에는 쓰지 않는다.
- `uuid`는 외부로 노출하는 식별자이며 중복될 수 없다. 발급한 토큰의 `sub`에 실린다.
  내부 PK(`id`)는 FK·조회에만 쓰고 밖으로 내보내지 않는다.
- `displayName`은 최초 등록 때 정해지고 **로그인으로 갱신하지 않는다.**
  provider 이름으로 매번 덮어쓰면 (1) 사용자가 직접 바꾼 이름이 다음 로그인에 지워지고,
  (2) 여러 provider를 연결한 뒤에는 마지막에 로그인한 쪽 이름으로 계속 뒤집힌다.
  바꾸는 수단은 `User.rename()`으로 열어둔다.
- `email`은 로그인할 때마다 provider가 준 값으로 갱신한다.
  최초 값의 박제가 아니라 "이 identity가 주장하는 현재 값"이기 때문이다.
  단 provider가 값을 주지 않으면 기존 값을 유지한다 — GitHub처럼 이메일을 비공개로 둔 계정은
  userinfo에 이메일을 주지 않는데, 그걸 "이메일이 없어졌다"로 해석하면 알던 값이 지워진다.
- `status`가 `ACTIVE`인 User만 로그인할 수 있고 새로운 작업을 생성할 수 있다.
  비활성화는 **즉시 반영된다.** 토큰의 `sub`로 매 요청 User를 조회하기 때문이다.
  요청당 조회가 한 번 늘지만, 토큰 만료를 기다리지 않고 차단할 수 있다.

### Ownership

- 사용자 소유 서비스는 `owner_user_uuid`로 사용자를 가리킨다. 작업·업로드·사용량은 `workspace_uuid`도 기록한다.
- 서비스 범위 요청은 인증된 사용자의 서비스인지 확인한다. 선택이 없으면 기본 서비스를 쓴다.
- 작업·파일 조회와 파일 입력 검증은 서비스 범위로 한다. 다른 서비스의 리소스는 없는 것과 같게 처리한다.
- Worker에는 요청 컨텍스트가 없으므로 작업에 저장된 `workspace_uuid`로 파일을 찾고 사용량을 기록한다.
- 모듈 경계를 넘는 FK는 두지 않고 UUID 값으로 참조한다. 모듈 안의 FK는 둔다.
- 계정 단위 합계는 인증된 사용자의 `owner_user_uuid`로 별도 조회한다.

## 4. 구현 원칙

- 도메인 규칙은 주석만으로 끝내지 않고 테스트로 검증한다.
- Provider별 상태값은 adapter에서 내부 Job 상태로 변환한다.
- 도메인 모델을 실제 사용하면서 발견한 규칙은 이 문서와 테스트에 함께 반영한다.

## 5. 구현 상태

구현 상태는 이 문서에 적지 않는다. 어디까지 왔는지는 [로드맵](../roadmap.md) 한 곳에 둔다.
