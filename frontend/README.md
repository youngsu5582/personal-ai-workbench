# Workbench 콘솔

React + TypeScript + Vite로 구성한 웹 콘솔이다. 서비스·작업·사용량 API 연결은 별도 작업이다.

```bash
cd frontend
npm ci
npm run dev
```

Vite 개발 서버는 `/api`, `/auth`, `/login`, `/oauth2`를 로컬 8080 포트로 전달한다. Spring에 콘솔을 포함해 실행하려면 먼저 `npm run build`를 실행한 뒤 프로젝트 루트에서 Gradle을 실행한다. Docker 이미지는 프런트를 자동으로 빌드해 포함한다.
