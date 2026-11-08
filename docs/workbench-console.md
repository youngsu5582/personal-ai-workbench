# Workbench 콘솔

React와 TypeScript로 구성한 콘솔이다. 로그인 토큰은 메모리에만 보관하므로 새로고침하면 다시 로그인해야 한다.

```bash
cd frontend
npm ci
npm run dev
```

Vite 개발 서버는 API와 로그인 요청을 로컬 8080 포트로 전달한다. Spring에 콘솔을 포함해 실행하려면 먼저 프론트 빌드를 실행한다. Docker 이미지는 프론트를 빌드해 포함한다.

## 콘솔 검사

프론트의 코드를 바꾸면 아래 명령으로 빌드와 검사를 실행한다. 브라우저 설치는 최초 실행이나 Playwright 버전이 바뀐 뒤 필요하다.

```bash
cd frontend
npm ci
npm run build
npm run check:architecture
npm run test:architecture
npx playwright install chromium
npm run test:browser
```

### 구조 검사

- 빌드는 타입을 확인하고 배포할 프론트 파일을 만든다.
- 의존성 검사는 공통 코드에서 기능 코드를 참조하는 경우와 순환 참조를 거절한다. 예를 들어 작업실 화면이 공통 이미지 컴포넌트를 사용하는 것은 가능하지만, 공통 이미지 컴포넌트가 작업실 화면을 참조하는 것은 불가능하다.
- 규칙 테스트는 잘못된 참조와 순환 참조를 임시 예제로 만들어 검사기가 실제로 거절하는지 확인한다.

현재 규칙의 정본은 [의존성 설정](../frontend/.dependency-cruiser.cjs)이고, 위반 예제는 [구조 규칙 테스트](../frontend/architecture-tests/rules.test.mjs)에 있다. 현재 규칙은 기능 간 참조 전체를 금지하지 않는다.

### 브라우저 검사와 서버 검증

[브라우저 테스트](../frontend/browser-tests/console.spec.ts)는 Playwright로 화면을 열고 API 응답을 모의한다. 작업 상세에서 서비스를 전환할 때 이전 서비스 결과가 사라지는지, 조회 오류 뒤 재시도되는지, 모바일 다크 모드에서 작업 내역을 볼 수 있는지 확인한다.

이 검사는 브라우저의 표시와 요청 흐름을 검증한다. 실제 서버 권한이나 DB 격리를 증명하지는 않는다. 서비스별 접근 범위는 서버 통합 테스트에서 별도로 확인한다. 브라우저에서 실제 서버와 DB까지 연결하는 E2E는 아직 없다. 외부 모델 유료 호출은 이 브라우저 테스트에 포함하지 않는다.

PR의 CI에서도 빌드, 구조 검사와 규칙 테스트, 브라우저 검사를 실행한다. 규칙을 바꾸면 설정과 위반 예제를 함께 살펴보고 필요한 화면 흐름을 테스트에 추가한다.
