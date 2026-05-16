# Deployment Hub

This is the canonical deployment guide for this repository.

If you only read one deployment document, read this file first.

## Choose a Path

- Render: fastest path to a public MVP.
- AWS ECS: full-control path for production scale.
- Local Docker Compose: development and validation on your machine.

Decision support:
- Platform comparison: [RENDER-vs-AWS.md](RENDER-vs-AWS.md)

Execution guides:
- Render detailed steps: [deploy/render/README.md](deploy/render/README.md)
- AWS ECS detailed steps: [deploy/aws-ecs/README.md](deploy/aws-ecs/README.md)

## Local Development

```powershell
docker compose up --build
```

URLs:
- Frontend: http://localhost:5173
- API Gateway: http://localhost:8080

## Render Path (Recommended for MVP)

1. Push the repo to GitHub.
2. In Render, create a new Blueprint.
3. Connect repo and let Render load `render.yaml`.
4. Set required environment variables.
5. Deploy and run health checks.

Helpers:

```powershell
./deploy/render/deploy-render.ps1 setup
```

```bash
bash deploy/render/render-deploy.sh validate
bash deploy/render/health-check.sh <API_GATEWAY_URL>
```

## AWS ECS Path (Recommended for Scale)

1. Build and push service images to ECR.
2. Provision RDS PostgreSQL and S3.
3. Update task definitions in `deploy/aws-ecs/`.
4. Register task definitions and create/update ECS services.
5. Configure ALB routing and verify health.

Task definition templates:
- `deploy/aws-ecs/taskdef-user-service.json`
- `deploy/aws-ecs/taskdef-candidate-service.json`
- `deploy/aws-ecs/taskdef-ai-ranking-service.json`
- `deploy/aws-ecs/taskdef-notification-service.json`
- `deploy/aws-ecs/taskdef-api-gateway.json`
- `deploy/aws-ecs/taskdef-frontend.json`

## Environment Variables (Minimum)

Local:

```env
JWT_SECRET=change-me
STORAGE_PROVIDER=local
```

Cloud with S3:

```env
JWT_SECRET=change-me
STORAGE_PROVIDER=s3
STORAGE_S3_BUCKET=<bucket>
AWS_REGION=<region>
AWS_ACCESS_KEY_ID=<key>
AWS_SECRET_ACCESS_KEY=<secret>
```

Optional:

```env
OPENAI_API_KEY=<optional>
USE_OPENAI=true
SMTP_PASSWORD=<optional>
```

## Validation After Deployment

Run these checks in order:

1. Frontend reachable
2. API gateway health endpoint reachable
3. Register -> Login -> Apply -> List -> Rank -> Invite flow passes

Local smoke test:

```powershell
./smoke-test.ps1
```

## Doc Ownership and Scope

To avoid duplication:
- `DEPLOYMENT.md`: entrypoint + deployment flow map (this file)
- `RENDER-vs-AWS.md`: comparison and decision support only
- `deploy/render/README.md`: Render implementation details only
- `deploy/aws-ecs/README.md`: AWS implementation details only
- `QUICK-REFERENCE.md`: compact command cheat sheet only
