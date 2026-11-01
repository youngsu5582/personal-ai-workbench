# Workbench 콘솔

React + TypeScript + Vite로 구성한 웹 콘솔이다. 기존 사용자 로그인과 서비스·작업·사용량·API 키 API를 연결한다. 로그인 토큰은 메모리에만 보관하므로 새로고침하면 다시 로그인해야 한다. 작업·생성 이미지 목록과 모델 목록은 API가 없어 아직 제공하지 않는다.

```bash
cd frontend
npm ci
npm run dev
```

Vite 개발 서버는 `/api`, `/auth`, `/login`, `/oauth2`를 로컬 8080 포트로 전달한다. Spring에 콘솔을 포함해 실행하려면 먼저 `npm run build`를 실행한 뒤 프로젝트 루트에서 Gradle을 실행한다. Docker 이미지는 프런트를 자동으로 빌드해 포함한다.
