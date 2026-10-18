# Home K3s deployment

This overlay is the first single-node home-server target for Personal AI Workbench.

## How traffic gets in

```
browser ─https─▶ Cloudflare (TLS ends here) ─tunnel─▶ cloudflared Pod ─http─▶ Traefik ─Ingress─▶ workbench / garage
```

- Only two hostnames are public: `workbench.youngsu5582.today` (the app) and
  `workbench-file.youngsu5582.today` (Garage, for presigned upload and download URLs).
  Everything else — the management port 8081, PostgreSQL, Garage RPC — is reachable
  only through cluster Services.
- Both are one level under the zone, so Cloudflare's free certificate
  (`*.youngsu5582.today`) covers them. A name like `api.workbench.youngsu5582.today`
  would not be covered.
- `cloudflared` is a pipe, not a router. Both hostnames go to Traefik, and Traefik
  splits them by `Host` through `overlays/home/ingress.yaml`.
- Traefik stays inside the cluster (`deploy/cluster/traefik.yaml` makes it ClusterIP)
  and trusts `X-Forwarded-*` only from the Pod network, where `cloudflared` runs.
  Without that, Traefik rewrites the proto to `http` and the app builds an `http`
  OAuth redirect URI, which Google rejects.

## Secrets

The overlay contains no Secret objects with real values. The operator creates:

| Secret | Keys | Source |
|---|---|---|
| `postgres-credentials` | `POSTGRES_PASSWORD` | chosen once |
| `garage-credentials` | `RPC_SECRET` | `openssl rand -hex 32` |
| `cloudflared-token` | `TUNNEL_TOKEN` | the tunnel created for this cluster |
| `ghcr-pull` | docker-registry | a GitHub token with `read:packages` |
| `workbench-runtime` | everything in `runtime.env.example` | a local `runtime.env` (ignored by Git) |

## Home server runbook

1. **Install k3s without the service load balancer**, so nothing in the cluster takes
   host ports 80/443 (other services on the host keep them):

   ```bash
   curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--disable servicelb" sh -
   ```

2. **Apply the cluster-level Traefik settings** and wait for Traefik to restart:

   ```bash
   kubectl apply -k deploy/cluster
   kubectl -n kube-system rollout status deployment/traefik
   ```

3. **Create a tunnel for this cluster** in Cloudflare Zero Trust and copy its token.
   Add two public hostnames, both pointing at Traefik:

   | Hostname | Service |
   |---|---|
   | `workbench.youngsu5582.today` | `http://traefik.kube-system.svc.cluster.local:80` |
   | `workbench-file.youngsu5582.today` | `http://traefik.kube-system.svc.cluster.local:80` |

   Do not put Cloudflare Access in front of `workbench`. CLI and MCP clients call it
   with Bearer tokens and cannot pass an Access login; the app authenticates them itself.

4. **Fill `runtime.env`** from `runtime.env.example`. The hostnames are already in it
   and in `overlays/home/ingress.yaml`; if you move them, change both together.

5. **Create the namespace and Secrets:**

   ```bash
   kubectl apply -f deploy/overlays/home/namespace.yaml
   kubectl -n workbench create secret generic postgres-credentials --from-literal=POSTGRES_PASSWORD='…'
   kubectl -n workbench create secret generic garage-credentials --from-literal=RPC_SECRET="$(openssl rand -hex 32)"
   kubectl -n workbench create secret generic cloudflared-token --from-literal=TUNNEL_TOKEN='…'
   kubectl -n workbench create secret docker-registry ghcr-pull \
     --docker-server=ghcr.io --docker-username=youngsu5582 --docker-password='…'
   kubectl -n workbench create secret generic workbench-runtime --from-env-file=deploy/overlays/home/runtime.env
   ```

6. **Register the redirect URI** `https://workbench.youngsu5582.today/login/oauth2/code/google`
   on the Google OAuth client.

7. **Pin an image digest** (see below) and apply:

   ```bash
   kubectl apply -k deploy/overlays/home
   kubectl -n workbench get pods -w
   ```

8. **Initialize Garage once.** A single node still has to declare its layout before
   it accepts data. `key create` prints the secret only once — copy it.

   ```bash
   NODE=$(kubectl -n workbench exec garage-0 -- /garage node id -q | cut -d@ -f1)
   kubectl -n workbench exec garage-0 -- /garage layout assign -z home -c 90G "$NODE"
   kubectl -n workbench exec garage-0 -- /garage layout apply --version 1
   kubectl -n workbench exec garage-0 -- /garage bucket create workbench
   kubectl -n workbench exec garage-0 -- /garage key create workbench-app
   kubectl -n workbench exec garage-0 -- /garage bucket allow --read --write workbench --key workbench-app
   ```

   Put the printed key ID and secret into `STORAGE_S3_ACCESS_KEY` / `STORAGE_S3_SECRET_KEY`
   in `runtime.env`, recreate `workbench-runtime`, and restart the app:

   ```bash
   kubectl -n workbench delete secret workbench-runtime
   kubectl -n workbench create secret generic workbench-runtime --from-env-file=deploy/overlays/home/runtime.env
   kubectl -n workbench rollout restart deployment/workbench
   ```

   Do not repeat the layout step against an existing Garage volume unless you have
   a recovery plan.

9. **Check** that `https://workbench.youngsu5582.today/` loads and Google login returns to it, then
   generate one image and open its download URL (it redirects to `workbench-file.youngsu5582.today`).

10. **Back up PostgreSQL off the node before keeping real data.** Volumes use k3s's
    local-path storage on this disk; deleting a PVC deletes its data.

## Deploying a version

Every push to `main` publishes an image, and `image.yml` writes the exact
reference to the run summary (Actions → the run → Summary):

```
ghcr.io/youngsu5582/personal-ai-workbench:sha-abc1234@sha256:9f86d081…
```

Pin that digest in `overlays/home/kustomization.yaml`, apply, and commit the
change so Git says what the cluster runs:

```yaml
images:
  - name: ghcr.io/youngsu5582/personal-ai-workbench
    digest: sha256:9f86d081…
```

```bash
kubectl apply -k deploy/overlays/home
kubectl -n workbench rollout status deployment/workbench
```

The overlay ships with an all-zero digest, so a first apply without a real
digest stops at image pull instead of running something nobody chose.

Do not pin the `main` tag. It moves, so applying it leaves the pod template
unchanged, and `IfNotPresent` keeps whatever image the node already cached.
`kubectl set image` works once, but the next `apply -k` puts the Git value
back. To roll back, pin the previous digest and apply again.
