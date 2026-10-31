# 홈서버 K3s 배포

Personal AI Workbench 를 단일 노드 홈서버의 K3s 에 올리는 매니페스트다.

| 경로 | 무엇 | 적용 |
|---|---|---|
| `base/` | 앱 하나: Deployment · Service · ServiceAccount | overlay 가 가져다 쓴다 |
| `overlays/home/` | 이 홈서버에만 있는 것: Namespace · PostgreSQL · Garage · 버킷 CORS · Ingress · cloudflared · 이미지 digest | `kubectl apply -k deploy/overlays/home` |
| `cluster/` | K3s 가 함께 띄우는 Traefik 의 설정. `kube-system` 에 있어서 overlay 와 따로 적용한다 | `kubectl apply -k deploy/cluster` |

서버에서는 kubeconfig 가 root 전용이라 `sudo k3s kubectl` 로 부른다.

## 요청이 들어오는 길

```
브라우저 ─https─▶ Cloudflare (TLS 종료) ─tunnel─▶ cloudflared Pod ─http─▶ Traefik ─Ingress─▶ workbench / garage
```

- 바깥에 여는 이름은 둘뿐이다. `workbench.youngsu5582.today`(앱)와 `workbench-file.youngsu5582.today`(서명 URL 로 파일을 주고받는 Garage).
  관리 포트 8081 · PostgreSQL · Garage RPC 는 Service 로만 닿는다.
- 공유기 포트 포워딩도, 호스트의 80/443 도 쓰지 않는다. 80/443 은 호스트의 Nginx Proxy Manager 가 갖고 있고, Traefik 은 ClusterIP 라 클러스터 안에서만 보인다.
- cloudflared 는 길을 나누지 않는다. 두 이름을 모두 Traefik 으로 넘기고, 이름별 분기는 `overlays/home/ingress.yaml` 이 한다.
- TLS 는 Cloudflare 가 끊고 클러스터 안은 http 다. 원래 https 였다는 사실은 `X-Forwarded-Proto` 로 전해진다.
  - Traefik 은 Pod 대역(`10.42.0.0/16`, cloudflared 가 있는 곳)이 보낸 이 헤더만 믿는다(`cluster/traefik.yaml`). 믿지 않으면 http 로 덮어, Google 로그인의 redirect_uri 가 http 가 된다.
  - 앱에는 따로 설정이 없다. Spring Boot 는 Kubernetes 에서 돌면 Tomcat 의 RemoteIpValve 를 켜고, 내부 대역(`10.0.0.0/8` 등)에서 온 헤더만 믿는다.
- `workbench` 앞에 Cloudflare Access 를 두지 않는다. CLI · MCP 클라이언트는 Bearer 토큰으로 부르므로 Access 로그인을 통과할 수 없다. 로그인은 앱이 막는다(`AUTH_ALLOWED_EMAILS`).

## K3s

ServiceLB 만 끄고, Traefik 은 켜 둔다. ServiceLB 는 `LoadBalancer` Service 를 호스트 포트로 여는데, 호스트의 80/443 은 NPM 이 쓴다.
Traefik 은 `cluster/traefik.yaml` 로 ClusterIP 가 되므로 호스트 포트를 잡지 않는다.

이미 설치된 K3s 의 옵션을 바꿀 때는 Traefik 설정을 먼저 두고, 설치 스크립트를 **지금 버전으로 고정해** 다시 돌린다.
버전을 정하지 않으면 스크립트가 최신으로 올린다.

설치 스크립트는 systemd 의 실행 인자를 `INSTALL_K3S_EXEC` 로 **통째로 바꾼다.** 먼저 지금 인자를 보고, `--disable=traefik` 만 빼고 나머지는 그대로 옮긴다.
`/etc/rancher/k3s/config.yaml` 은 스크립트가 건드리지 않으니, 거기에 `disable` 이 있으면 따로 고친다.

```bash
systemctl cat k3s | sed -n '/^ExecStart=/,/^$/p'     # 지금 인자
sudo cat /etc/rancher/k3s/config.yaml 2>/dev/null     # 있으면 함께 본다
sudo ls /var/lib/rancher/k3s/server/manifests/        # traefik.yaml.skip 이 있으면 Traefik 이 다시 뜨지 않는다

sudo k3s kubectl apply -k deploy/cluster
curl -sfL https://get.k3s.io | sudo env \
  INSTALL_K3S_VERSION="$(k3s --version | awk 'NR==1{print $3}')" \
  INSTALL_K3S_EXEC="server --disable=servicelb" sh -    # 지금 인자에서 --disable=traefik 만 뺀 것
```

확인할 것:

```bash
sudo k3s kubectl -n kube-system get helmchart traefik   # 다시 생겼다
sudo k3s kubectl -n kube-system get svc traefik         # TYPE 이 ClusterIP
sudo k3s kubectl get pods -A | grep svclb               # 비어 있다
sudo ss -lntp | grep -E ':(80|443) '                    # 주인이 그대로 NPM(docker-proxy)
```

Traefik 의 HelmChart 나 Service 가 몇 분이 지나도 생기지 않으면 멈춘다. 그 상태로 터널을 넘기면 두 이름이 502 를 낸다.

`cluster/traefik.yaml` 이 믿는 `10.42.0.0/16` 은 K3s 기본 Pod 대역이다. 다른 대역으로 설치했다면 그 값으로 바꾼다(`sudo k3s kubectl get node -o jsonpath='{.items[0].spec.podCIDR}'`).

## Cloudflare Tunnel

이 클러스터용 터널을 따로 만든다. 호스트의 다른 서비스가 쓰는 터널과 섞지 않는다.
토큰은 Secret 으로 넣고(아래), Public hostname 두 개를 둘 다 Traefik 으로 보낸다.

| 이름 | Service |
|---|---|
| `workbench.youngsu5582.today` | `http://traefik.kube-system.svc.cluster.local:80` |
| `workbench-file.youngsu5582.today` | `http://traefik.kube-system.svc.cluster.local:80` |

`workbench-file` 은 Cache Rules 로 캐시를 우회한다. 서명 GET URL 은 만료돼야 하는데, 엣지에 캐시되면 만료 뒤에도 파일이 나간다.
서명 다운로드 응답에도 `Cache-Control: private, no-store` 가 붙지만(서명에 `response-cache-control` 을 넣는다), 다른 캐시 규칙이 덮어쓰지 못하게 규칙을 함께 둔다.

## Secret

overlay 에는 값이 든 Secret 이 없다. 운영자가 만든다.

| Secret | 키 | 출처 |
|---|---|---|
| `postgres-credentials` | `POSTGRES_PASSWORD` | 처음 정한 값 |
| `garage-credentials` | `RPC_SECRET` | `openssl rand -hex 32` |
| `workbench-runtime` | `overlays/home/runtime.env.example` 의 모든 키 | 로컬 `runtime.env` (Git 제외, mode 600) |
| `cloudflared-token` | `TUNNEL_TOKEN` | 위 터널의 토큰 |

- `workbench-runtime` 의 `DB_PASSWORD` 는 `POSTGRES_PASSWORD` 와 같은 값이다.
- `workbench-runtime` 의 S3 키는 Garage 의 RPC secret 과 다른 것이다. Garage 가 뜬 뒤 발급하고, 버킷 CORS 를 넣을 수 있게 **owner 권한까지** 준다.
  앱 Pod 의 initContainer 가 배포 때마다 CORS 를 다시 넣는데, 읽기 · 쓰기만으로는 버킷 설정을 바꿀 수 없다.

  ```bash
  sudo k3s kubectl -n workbench exec garage-0 -- /garage bucket allow --read --write --owner workbench --key workbench-app
  ```

- 토큰은 명령 인자나 셸 history 에 남기지 않는다.

  ```bash
  read -rs TOKEN
  printf %s "$TOKEN" | sudo k3s kubectl -n workbench create secret generic cloudflared-token \
    --from-file=TUNNEL_TOKEN=/dev/stdin
  unset TOKEN
  ```

## 새 버전 배포

`main` 에 머지하면 `image.yml` 이 이미지를 GHCR 에 올리고, 실행 요약(Actions → 해당 실행 → Summary)에 digest 를 남긴다.
그 값을 `overlays/home/kustomization.yaml` 의 `images[].digest` 에 적고, 적용한 뒤 커밋한다. 그래야 클러스터에 무엇이 떠 있는지 Git 이 말한다.

```bash
sudo k3s kubectl diff -k deploy/overlays/home     # live 와 무엇이 다른지
sudo k3s kubectl apply -k deploy/overlays/home
sudo k3s kubectl -n workbench rollout status deployment/workbench
```

태그(`main`)로는 고정하지 않는다. 태그가 옮겨 가도 Pod 템플릿이 바뀌지 않아, `IfNotPresent` 가 노드에 캐시된 옛 이미지를 계속 쓴다.
`kubectl set image` 는 한 번은 되지만 다음 `apply -k` 가 Git 의 값으로 되돌린다. 되돌릴 때도 이전 digest 를 적고 다시 적용한다.
