# Plan: 업무 지표를 Prometheus 로 내보낸다
Created: 2026-10-18
Status: active

## Progress
- [x] 1단계 앱 metric — Provider 호출 Timer (#60)
- [ ] 1단계 후속 — Task 시도 기록(새 테이블 없이 Task 행 안에) · 시도 Timer(재시도율) · 보관 Timer · 로그 추적(MDC) (#60 위에 쌓는 PR)
- [x] 2단계 로컬 compose 에 Prometheus · Grafana — 대시보드와 PromQL 을 k3s 없이 먼저 만든다 (profile `observability`)
- [ ] 2단계 후속 — Loki · Alloy. 1단계 후속의 MDC 가 들어간 뒤 붙인다 — 그래야 Task 하나로 로그를 모을 수 있다
- [ ] k3s 학습 — #56 의 커밋을 하나씩 Rancher Desktop 에 올려 본다
- [ ] 3단계 홈서버 클러스터에 monitoring 층 — Prometheus · Grafana · node-exporter 매니페스트

## 결정
- 스택은 Prometheus + Grafana 를 매니페스트로 직접 둔다. kube-prometheus-stack 은 operator 라는 층이 하나 더라 k3s 와 같이 배우면 막힌 곳을 가르기 어렵다. 옮겨도 PromQL · 대시보드는 그대로 쓴다
- Provider 지표는 Timer 하나로 접는다. 횟수(`_count`) · 실패율(outcome tag) · 분포(`_bucket`)를 함께 답해 노트 3편의 세 이름이 필요 없다
- 셈은 `DefaultProviderCallRecorder` 에서 한다. 모든 Provider 호출이 지나는 길이고 지연 · 성공 · 실패 종류가 이미 커맨드에 있다
- tag 값은 enum 이름과 따로 고정한다(`no_response` 등). 대시보드와 알림이 이 값에 묶인다
- 지표마다 설명(`# HELP`)을 붙인다. 이름만 봐서는 보관 시간이 들어 있는지 같은 것을 알 수 없다
- 진행 중인 Provider 호출을 `LongTaskTimer`(`workbench.provider.call.active`)로 보인다. 끝난 호출만 재는 Timer 로는 멈춘 호출이 타임아웃이 나야 보인다. 진행 중 표식(시작 시각 하나)만 메모리에 두고 끝나면 뺀다 — start·stop 을 손으로 짝짓지 않고 `LongTaskTimer.record` 로 감싸 성공이든 예외든 빠지게 한다. 동시 호출은 `batchSize`(4)를 넘지 않는다. `_count` 는 누적이 아니라 지금 수다
- 큐 길이 · 가장 오래 기다린 시간은 미룬다. 지금은 Provider · Task 시간을 읽기 쉽게 하고 추적할 수 있게 하는 것이 먼저다. 다시 할 때 고를 것:
  - Grafana 에서 Postgres 를 직접 조회한다 — 앱이 DB 를 주기마다 묻지 않고, 큐 길이는 앱이 아니라 DB 의 사실이다
  - 앱 Gauge 로 둔다면 긁을 때 묻지 말고 주기마다 읽어 둔다. 긁을 때 물으면 DB 가 막혔을 때 응답 전체가 기다려 다른 지표까지 빈다. 읽지 못하면 0 이 아니라 NaN, 인스턴스가 늘면 같은 값을 여러 번 보고하니 `max` 로 집계한다

## Provider 를 볼 때 쓰는 쿼리
요청이 드물어 `rate` 보다 `increase` 로 묶는 편이 읽기 쉽다. 2단계에서 실제 데이터로 확인한다.

| 보고 싶은 것 | PromQL |
|---|---|
| Provider 별 성공률 (1시간) | `sum by (provider) (increase(workbench_provider_call_seconds_count{outcome="success"}[1h])) / sum by (provider) (increase(workbench_provider_call_seconds_count[1h]))` |
| 어떻게 실패하나 | `sum by (provider, failure_kind) (increase(workbench_provider_call_seconds_count{outcome="failure"}[1h]))` |
| p95 지연 | `histogram_quantile(0.95, sum by (provider, le) (rate(workbench_provider_call_seconds_bucket[1h])))` |
| 한도에 걸리기 시작했나 (알림 후보) | `increase(workbench_provider_call_seconds_count{failure_kind="throttled"}[15m]) > 0` |
| 지금 진행 중인 호출 수 | `sum by (provider) (workbench_provider_call_active_seconds_count)` |
| 오래 응답이 없는 호출 (알림 후보, 기준은 읽기 타임아웃보다 짧게) | `max by (provider) (workbench_provider_call_active_seconds_max) > 300` |

## 다음에 볼 Provider 지표 후보
- **입력 재전송 횟수** — Provider 가 입력 주소를 받아가지 못해 바이트로 다시 보낸 경우가 원장에서 `REJECTED` 로 옮겨져(`ProviderCallMapper`) 지표에서도 `failure_kind="rejected"` 에 섞인다. 과금 판정으로는 맞는 매핑이라 원장은 두고, 워커의 재전송 분기에 Counter 를 따로 두면 "이 Provider 는 주소 입력을 못 받아간다" 가 갈린다
- **품질 tag** — 지연은 품질에 크게 좌우된다. Provider 를 같은 조건끼리 비교하려면 `quality` 를 tag 로 붙인다. 값의 가짓수를 먼저 확인한다
- **토큰 · 비용** — 지표로 두지 않는다. 사용량은 Provider 마다 모양이 다른 원문(`ProviderUsage.raw`)이고 비용은 Provider 가 직접 답할 때만 있다. `provider_calls` 에 쌓이는 원문으로 단가표 작업 때 계산한다

## Resume Point
1단계 후속 — #60 위에 쌓는 PR. Task 하나가 어디서 시간을 썼는지 지표 · DB · 로그로 남긴다.
1. Task 시도 기록 — 새 테이블 없이 Task 행 안에 둔다. 모양(진행 중인 시도의 `started_at` + 끝난 시도의 `attempts jsonb`)은 그 PR 에서 확정한다
2. 시도 Timer(outcome 별, 재시도율은 `retried` 개수) · 보관 Timer, 둘 다 설명을 붙인다
3. 로그 추적 — MDC 로 job · task · 시도 번호를 모든 워커 로그에 싣는다. 실패 · 재시도 로그에도 소요 시간

2단계(로컬 Prometheus · Grafana)는 끝났다. 쓰는 법은 `docs/development.md` 의 "지표 보기". 정해 둔 것:
- 앱과 개발 DB 를 `host.docker.internal` 로 부른다. 개발 DB 가 다른 워크트리의 compose 에서 떠 있을 수 있어 서비스 이름으로는 못 닿는다. Rancher Desktop 에서 `host-gateway` 를 덧붙이면 VM 브리지로 바뀌어 오히려 닿지 않는다
- 쌓인 Task 수 · 가장 오래 기다린 시간은 앱 지표가 아니라 Grafana 의 Postgres 데이터소스로 센다. 미뤄 둔 큐 지표가 여기서 풀렸다
- 3단계에서 대시보드 JSON 을 다시 쓸 때, kustomize 는 기본으로 자기 디렉터리 밖 파일을 못 읽는다. 그때 위치나 복사 방식을 정한다. Grafana 의 DB 계정도 그때 읽기 전용으로 따로 둔다
