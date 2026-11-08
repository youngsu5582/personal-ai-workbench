# Workbench 콘솔

React와 TypeScript로 구성한 콘솔이다. 로그인 토큰은 메모리에만 보관하므로 새로고침하면 다시 로그인해야 한다.

```bash
cd frontend
npm ci
npm run dev
```

Vite 개발 서버는 API와 로그인 요청을 로컬 8080 포트로 전달한다. Spring에 콘솔을 포함해 실행하려면 먼저 프론트 빌드를 실행한다. Docker 이미지는 프론트를 빌드해 포함한다.

## 콘솔 검사

```bash
cd frontend
npm run build
npm run check:architecture
npm run test:architecture
npx playwright install chromium
npm run test:browser
```

브라우저 테스트는 API 응답을 대체하므로 화면과 요청 흐름을 확인한다. 실제 서버와 DB의 연동은 서버 통합 테스트에서 별도로 확인한다.
