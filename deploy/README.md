# 홈서버 K3s 배포

이 오버레이는 Personal AI Workbench를 단일 노드 홈서버에 처음 배포하기 위한 구성이다.

## 요청이 들어오는 경로

```
브라우저 ─https─▶ Cloudflare (TLS 종료) ─tunnel─▶ cloudflared Pod ─http─▶ Traefik ─Ingress─▶ workbench / garage
```

- 외부에 공개하는 호스트는 `workbench.youngsu5582.today`(앱)와
  `workbench-file.youngsu5582.today`(업로드·다운로드 서명 URL을 받는 Garage)뿐이다.
  관리 포트 8081, PostgreSQL, Garage RPC는 클러스터 Service로만 접근한다.
- 두 호스트 모두 zone 바로 아래 한 단계이므로 Cloudflare 무료 인증서
  (`*.youngsu5582.today`)가 적용된다. `api.workbench.youngsu5582.today`처럼
  더 깊은 이름에는 적용되지 않는다.
- `cloudflared`는 두 호스트의 요청을 Traefik으로 전달한다. Traefik은
  `overlays/home/ingress.yaml`에 따라 `Host`별로 요청을 나눈다.
- Traefik은 클러스터 내부에만 둔다(`deploy/cluster/traefik.yaml`의 ClusterIP).
  `X-Forwarded-*`는 `cloudflared`가 실행되는 Pod 네트워크에서 온 경우에만 신뢰한다.
  이 설정이 없으면 Traefik이 프로토콜을 `http`로 바꾸고 앱이 만드는 OAuth 리다이렉트 URI도
  `http`가 되어 Google이 거절한다.

## Secret

오버레이에는 실제 값이 든 Secret이 없다. 운영자가 다음 Secret을 만든다.

| Secret | 키 | 출처 |
|---|---|---|
| `postgres-credentials` | `POSTGRES_PASSWORD` | 최초에 정한 값 |
| `garage-credentials` | `RPC_SECRET` | `openssl rand -hex 32` |
| `cloudflared-token` | `TUNNEL_TOKEN` | 이 클러스터에 만든 터널의 토큰 |
| `ghcr-pull` | docker-registry | `read:packages` 권한이 있는 GitHub 토큰 |
| `workbench-runtime` | `runtime.env.example`의 모든 항목 | 로컬 `runtime.env` (Git에서 제외) |

## 홈서버 운영 절차

모든 명령은 서버의 이 저장소 작업 디렉터리에서 실행한다.

1. **서비스 로드밸런서를 끄고 k3s를 설치한다.** 호스트의 기존 서비스가 80/443 포트를
   계속 사용하도록 클러스터가 해당 포트를 차지하지 않는다. k3s가 만든 kubeconfig는
   root만 읽을 수 있으므로 현재 사용자용 설정도 만든다.

   ```bash
   curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--disable servicelb" sh -
   mkdir -p ~/.kube
   sudo k3s kubectl config view --raw > ~/.kube/config
   chmod 600 ~/.kube/config
   export KUBECONFIG=~/.kube/config   # ~/.bashrc에도 추가한다
   ```

2. **클러스터 공통 Traefik 설정을 적용하고** Traefik이 다시 뜰 때까지 기다린다.

   ```bash
   kubectl apply -k deploy/cluster
   kubectl -n kube-system rollout status deployment/traefik
   ```

3. Cloudflare Zero Trust에서 **이 클러스터용 터널을 만들고** 토큰을 복사한다.
   두 공개 호스트를 추가하고 모두 Traefik을 가리키게 한다.

   | 호스트 이름 | 서비스 |
   |---|---|
   | `workbench.youngsu5582.today` | `http://traefik.kube-system.svc.cluster.local:80` |
   | `workbench-file.youngsu5582.today` | `http://traefik.kube-system.svc.cluster.local:80` |

   `workbench` 앞에는 Cloudflare Access를 두지 않는다. CLI와 MCP 클라이언트는
   Bearer 토큰으로 호출하므로 Access 로그인을 통과할 수 없다. 앱이 직접 인증한다.

   Cloudflare Cache Rules에서 호스트 `workbench-file.youngsu5582.today`의 캐시를 우회한다.
   서명 다운로드도 `Cache-Control: private, no-store`를 보내지만 캐시 우회 규칙을
   추가 방어로 두고 다른 캐시 규칙이 이를 덮어쓰지 않는지 확인한다. 이 호스트에서 이미
   파일을 제공했다면 규칙을 켤 때 기존 캐시를 삭제한다.

4. `runtime.env.example`을 바탕으로 **`runtime.env`를 채운다.** 호스트 이름은 이 파일과
   `overlays/home/ingress.yaml`에 이미 들어 있다. 이름을 바꾸면 두 곳을 함께 고친다.
   `AUTH_ALLOWED_EMAILS`를 비워 두지 않는다. 로그인 페이지가 공개되어 있어 목록이 비면
   모든 Google 계정이 로그인해 토큰을 받고 유료 Provider를 호출할 수 있다.

5. **네임스페이스와 Secret을 만든다.**

   ```bash
   kubectl apply -f deploy/overlays/home/namespace.yaml
   kubectl -n workbench create secret generic postgres-credentials --from-literal=POSTGRES_PASSWORD='…'
   kubectl -n workbench create secret generic garage-credentials --from-literal=RPC_SECRET="$(openssl rand -hex 32)"
   kubectl -n workbench create secret generic cloudflared-token --from-literal=TUNNEL_TOKEN='…'
   kubectl -n workbench create secret docker-registry ghcr-pull \
     --docker-server=ghcr.io --docker-username=youngsu5582 --docker-password='…'
   kubectl -n workbench create secret generic workbench-runtime --from-env-file=deploy/overlays/home/runtime.env
   ```

6. Google OAuth 클라이언트에 **리다이렉트 URI**
   `https://workbench.youngsu5582.today/login/oauth2/code/google`을 등록한다.

7. **이미지 다이제스트(digest)를 고정하고**(아래 참조) 적용한다.

   ```bash
   kubectl apply -k deploy/overlays/home
   kubectl -n workbench get pods -w
   ```

8. **Garage를 최초 한 번 초기화한다.** 단일 노드여도 데이터를 받기 전에 레이아웃을
   선언해야 한다. `key create`는 비밀 키를 한 번만 출력하므로 복사해 둔다.

   ```bash
   NODE=$(kubectl -n workbench exec garage-0 -- /garage node id -q | cut -d@ -f1)
   kubectl -n workbench exec garage-0 -- /garage layout assign -z home -c 90G "$NODE"
   kubectl -n workbench exec garage-0 -- /garage layout apply --version 1
   kubectl -n workbench exec garage-0 -- /garage bucket create workbench
   kubectl -n workbench exec garage-0 -- /garage key create workbench-app
   kubectl -n workbench exec garage-0 -- /garage bucket allow --read --write workbench --key workbench-app
   ```

   출력된 키 ID와 비밀 키를 `runtime.env`의 `STORAGE_S3_ACCESS_KEY`와
   `STORAGE_S3_SECRET_KEY`에 넣고 `workbench-runtime`을 다시 만든 뒤 앱을 재시작한다.

   ```bash
   kubectl -n workbench delete secret workbench-runtime
   kubectl -n workbench create secret generic workbench-runtime --from-env-file=deploy/overlays/home/runtime.env
   kubectl -n workbench rollout restart deployment/workbench
   ```

   복구 계획 없이 기존 Garage 볼륨에서 레이아웃 단계를 다시 실행하지 않는다.

9. **브라우저가 Garage에 업로드할 수 있게 한다.**
   `workbench.youngsu5582.today`의 페이지는 다른 출처(origin)인
   `workbench-file.youngsu5582.today`에 직접 PUT하므로 버킷에 CORS 규칙이 필요하다.
   앱의 CORS 설정은 여기에 적용되지 않는다. 스크립트는 `aws`, `curl`, `python3`가
   필요하며 사전 요청(preflight)도 확인한다.

   ```bash
   set -a; . deploy/overlays/home/runtime.env; set +a
   ./scripts/upload-cors.sh https://workbench.youngsu5582.today
   ```

10. `https://workbench.youngsu5582.today/`가 열리고 Google 로그인 후 돌아오는지
    **확인한다.** 페이지에서 이미지 하나를 업로드하고, 이미지 하나를 생성한 뒤 다운로드
    URL을 연다(`workbench-file.youngsu5582.today`로 리다이렉트된다).
    파일 응답에 `Cache-Control: private, no-store`가 있고 Cloudflare 캐시에서 제공되지
    않는지 확인한다. 같은 서명 URL로 만료 전에 한 번, 만료 후에 다시 요청한다.
    두 번째 요청은 파일을 반환하면 안 된다. 서명 URL은 자격증명처럼 취급하고 로그나
    공유 티켓에 붙여 넣지 않는다.

11. 실제 데이터를 보관하기 전에 **PostgreSQL을 노드 밖에 백업한다.** 볼륨은 이 디스크의
    k3s local-path 스토리지를 사용하므로 PVC를 삭제하면 데이터도 삭제된다.

## 새 버전 배포

`main`에 푸시할 때마다 이미지가 발행되고 `image.yml`이 정확한 이미지 참조를
실행 요약(Actions → 해당 실행 → Summary)에 남긴다.

```
ghcr.io/youngsu5582/personal-ai-workbench:sha-abc1234@sha256:9f86d081…
```

이 다이제스트를 `overlays/home/kustomization.yaml`에 고정하고 적용한 뒤 커밋한다.
그러면 클러스터에서 실행하는 버전이 Git에 남는다.

```yaml
images:
  - name: ghcr.io/youngsu5582/personal-ai-workbench
    digest: sha256:9f86d081…
```

```bash
kubectl apply -k deploy/overlays/home
kubectl -n workbench rollout status deployment/workbench
```

오버레이에는 0으로만 채운 다이제스트가 들어 있다. 실제 다이제스트를 고르지 않고
처음 적용하면 이미지를 가져오지 못해 멈춘다.

`main` 태그는 고정 대상으로 쓰지 않는다. 태그가 이동해도 Pod 템플릿이 바뀌지 않아
`IfNotPresent`가 노드에 캐시된 기존 이미지를 계속 쓴다. `kubectl set image`는
한 번 동작하지만 다음 `apply -k`가 Git의 값으로 되돌린다. 롤백하려면 이전
다이제스트를 고정하고 다시 적용한다.
