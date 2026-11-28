# Plan: 결과 이미지 썸네일
Created: 2026-11-28
Status: paused — 라이브러리 PR 머지 뒤 main 에서 시작

## 결정
- 방식: **생성 시점에 미리 만든다(A)**. 요청 시 생성하는 이미지 서버(imgproxy, B)는 아래 신호가 오면 분리
- 엔진: libvips CLI `vipsthumbnail` 을 subprocess 로. JDK 21 유지(vips-ffm 은 JDK 23+). sharp 는 Node 라 제외
  - subprocess 인 이유: 네이티브 처리가 멈추거나 메모리를 먹어도 JVM 이 아니라 그 프로세스만 죽는다. 시간 제한을 프로세스 단위로 건다(Wikimedia Thumbor 와 같은 이유)
- 출력: WebP(투명 지원 → 형식 분기 없음), **짧은 변** 512px, 키우지 않음, 품질 ~80(실측으로 조정)
- 저장: 보관소에 `BlobKey.of(…, sha256(썸네일), "image/webp")` 로 put. DB 는 `generated_files.previews jsonb NULL` 에 `{ thumbnail: {storageKey,width,height,mimeType,fileSize} }` — 같은 INSERT, UPDATE 없음
  - 새 행(GeneratedFile)으로 넣지 않는다: 결과 목록 · 공개 해시에 섞인다
  - 쓰임새는 Map 키가 아니라 필드(`FilePreviews(thumbnail)`). 작은 값(blurhash · 파형)은 `metadata`, 변환본(압축 오디오)은 previews 아님
- 코드: domain `ImageResizer` 포트 + infrastructure `VipsImageResizer`. `.claude/conventions.json` 에 `Resizer` · `Vips` 규칙 추가
- 실패: 썸네일 실패해도 Task 성공. 콘솔은 원본으로 폴백. 옛 행 백필 안 함
- API: `FileResponse.Image.thumbnail = { url, previewUrl, width, height } | null`, 앱 경로 `/api/files/{sha256}/thumbnail`
- 설치: Dockerfile 런타임 `apt-get install libvips-tools`, CI 동일, 로컬 `brew install vips`(docs/development.md). 없으면 오류 메시지가 설치를 안내

## 분리(B) 신호
- 크기 2~3개 이상(display · srcset) / 외부 서비스가 임의 크기 요청
- 옛 결과에도 썸네일 필요(요청 시 생성이면 백필 불필요)
- 업로드 파일도 썸네일 필요(모듈 둘이 한 서버 공유)
- 워커 CPU · 메모리 압박(비디오 포스터 등)

## 분리를 싸게 두는 경계
- 콘솔은 받은 주소로만 그린다 / 주소 발급(소유권 확인)은 우리 API 만 / 엔진은 포트 뒤

## Progress
- [ ] Docker 빌드로 libvips-tools 의 WebP 쓰기 · 이미지 증가량 확인 (Dockerfile · CI diff 는 사용자 확인 후)
- [ ] 마이그레이션 `previews jsonb` + `FilePreviews` + 영속성 테스트
- [ ] `ImageResizer` · `VipsImageResizer` + 단위 테스트(짧은 변 · 키우지 않음 · 투명 · 깨진 바이트 · 시간 초과)
- [ ] 워커 연결 + 응답 `thumbnail` + `/api/files/{sha256}/thumbnail`
- [ ] 콘솔 라이브러리 썸네일 사용(폴백 유지)
- [ ] 실측: 크기 · 처리 시간 → PR 본문
- [ ] ADR: 썸네일 생성 방식(A vs B, 업계 사례 비교)

## Resume Point
라이브러리 PR(`feat/library`) 머지 확인 → main 에서 워크트리 생성(`.claude/scripts/worktree.sh`) → Progress 첫 항목.

## Files
- INDEX.md — 이 파일
