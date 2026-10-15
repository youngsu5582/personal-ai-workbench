# Home K3s deployment

This overlay is the first single-node home-server target for Personal AI Workbench.
It intentionally keeps credentials outside Git:

- `postgres-credentials` is created manually and supplies PostgreSQL's password.
- `garage-credentials` is created manually and supplies Garage's `RPC_SECRET`.
- `workbench-runtime` is created from a local copy of `runtime.env.example`.
- `ghcr-pull` and `workbench-public-tls` are supplied by the operator.

The overlay contains no Secret objects with real values. Replace the two
`example.com` hosts in `ingress.yaml` before applying, then render it with:

```bash
kubectl kustomize deploy/overlays/home
```

Garage's layout, bucket, and key are deliberately initialized after the first
successful StatefulSet rollout. Do not repeat layout initialization against an
existing Garage PVC unless you have a recovery plan.
