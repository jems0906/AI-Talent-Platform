# Quick Reference

Command-first cheat sheet for daily operations.

For full deployment guidance, use [DEPLOYMENT.md](DEPLOYMENT.md).

## Local Run

```powershell
docker compose up --build
```

```powershell
docker compose up -d --build --force-recreate
```

```powershell
docker compose ps
```

## Local Health Checks

```powershell
(Invoke-WebRequest -Uri http://localhost:5173 -UseBasicParsing).StatusCode
```

```powershell
(Invoke-WebRequest -Uri http://localhost:8080/health -UseBasicParsing).Content
```

## Smoke Test

```powershell
./smoke-test.ps1
```

```powershell
./smoke-test.ps1 -RecruiterPassword (ConvertTo-SecureString "Password123!" -AsPlainText -Force)
```

## Render

```powershell
./deploy/render/deploy-render.ps1 setup
```

```bash
bash deploy/render/render-deploy.sh validate
bash deploy/render/health-check.sh <API_GATEWAY_URL>
```

Render full guide: [deploy/render/README.md](deploy/render/README.md)

## AWS ECS

```bash
aws ecs register-task-definition --cli-input-json file://deploy/aws-ecs/taskdef-api-gateway.json
```

```bash
aws ecs describe-services --cluster <CLUSTER> --services api-gateway
```

AWS full guide: [deploy/aws-ecs/README.md](deploy/aws-ecs/README.md)

## Cleanup

```powershell
docker compose down
```

```powershell
docker compose down -v
```

## Decision Support

- Platform comparison: [RENDER-vs-AWS.md](RENDER-vs-AWS.md)
- Deployment entrypoint: [DEPLOYMENT.md](DEPLOYMENT.md)
