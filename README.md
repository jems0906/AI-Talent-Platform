# AI-Powered Talent Experience Platform (Mini Phenom Clone)

A simplified multi-tenant SaaS recruiting platform that demonstrates:
- Candidate application portal
- Recruiter dashboard
- AI resume ranking against job descriptions
- Resume text extraction for txt, pdf, and docx uploads
- Interview invite notifications via SMTP/SendGrid
- Microservice architecture with Docker
- AWS ECS deployment artifacts

## Services

- frontend (React + Vite)
- user-service (Spring Boot): register/login with JWT
- candidate-service (Spring Boot): applications, storage, ranking orchestration, invites
- api-gateway (Spring Boot WebFlux): centralized routing and JWT/tenant guard for recruiter APIs
- ai-ranking-service (FastAPI): NLP-style keyword ranking + optional OpenAI scoring
- notification-service (Spring Boot): SMTP email notifications with local simulation fallback

## Architecture

- Multi-tenant by tenantId (provided during application and recruiter login)
- Frontend calls `/api` on the same origin and Nginx proxies those requests to the API gateway
- Recruiter APIs in candidate-service require Bearer JWT + tenant match
- User and candidate data persist in PostgreSQL (Docker service: postgres)
- Database schema is managed with Flyway migrations per service
- Service-to-service REST calls:
   - frontend -> api-gateway (single ingress)
   - api-gateway -> user-service/candidate-service/notification-service
  - candidate-service -> ai-ranking-service (/rank)
  - candidate-service -> notification-service (/api/notifications/email)
- File storage:
  - Local volume-backed storage in Docker Compose
   - Optional S3-backed storage for AWS deployment via `STORAGE_PROVIDER=s3`

## Run Locally with Docker

1. Copy env template:
   - Windows PowerShell: `Copy-Item .env.example .env`
2. Start all services:
   - `docker compose up --build`
3. Open the platform:
   - Frontend: http://localhost:5173
4. Service endpoints:
   - API Gateway: http://localhost:8080
   - User API (internal): http://localhost:8081
   - Candidate API (internal): http://localhost:8082
   - Notification API (internal): http://localhost:8083
   - AI API: http://localhost:8084
   - PostgreSQL: localhost:5432

## Quick Smoke Test

Run an end-to-end API validation (register/login, apply, list, rank, invite):

```powershell
./smoke-test.ps1
```

If you omit `-RecruiterPassword`, the script prompts securely for it.

Non-interactive example:

```powershell
./smoke-test.ps1 -RecruiterPassword (ConvertTo-SecureString "Password123!" -AsPlainText -Force)
```

Optional parameters:

```powershell
./smoke-test.ps1 -TenantId acme -RecruiterEmail recruiter@test.local -RecruiterPassword Password123! -ResumePath ./sample-resume.txt
```

## Troubleshooting

Restart all services:

```powershell
docker compose restart
```

Check service health:

```powershell
docker compose ps
```

Check frontend and gateway quickly:

```powershell
(Invoke-WebRequest -Uri http://localhost:5173 -UseBasicParsing).StatusCode
(Invoke-WebRequest -Uri http://localhost:8080/health -UseBasicParsing).Content
```

If startup was interrupted and you see stale container conflicts, recreate cleanly:

```powershell
docker compose up -d --build --force-recreate
```

## User Flow Demo

1. Candidate Portal
   - Enter tenantId and jobId
   - Upload resume and submit
2. Recruiter Dashboard
   - Register recruiter under same tenantId
   - Refresh candidates
   - Add job description and click AI Rank
   - Click Send Invite

## OpenAI Integration (Optional)

Set in .env:
- USE_OPENAI=true
- OPENAI_API_KEY=<your-key>

If disabled or unavailable, the AI service falls back to local keyword matching.

## Storage Modes

Set in .env:
- `STORAGE_PROVIDER=local` for Docker/local development
- `STORAGE_PROVIDER=s3` for AWS deployment
- `STORAGE_S3_BUCKET=<bucket-name>` and `AWS_REGION=<region>` when using S3

When S3 is enabled, resumes are uploaded to S3 and extracted resume text is persisted in PostgreSQL for later ranking.

## Cloud Deployment

Cloud deployment docs were consolidated to avoid duplicated instructions:

- Start with **[DEPLOYMENT.md](DEPLOYMENT.md)** for platform selection and end-to-end deployment flow.
- Use **[RENDER-vs-AWS.md](RENDER-vs-AWS.md)** for cost and architecture comparison.
- Use **[deploy/render/README.md](deploy/render/README.md)** for Render-specific execution details.
- Use **[deploy/aws-ecs/README.md](deploy/aws-ecs/README.md)** for AWS ECS-specific execution details.

Quick entry points:

- Render helper (Windows): `./deploy/render/deploy-render.ps1 setup`
- Render helper (macOS/Linux): `bash deploy/render/render-deploy.sh validate`
- AWS assets: `deploy/aws-ecs/taskdef-*.json`

## Security Notes

- JWT is implemented for auth simulation in user-service
- Replace default secrets before deployment
- Enable stricter CORS and role authorization for production
- Use `STORAGE_PROVIDER=s3` for production-scale deployments

## Tech Stack Summary

- Frontend: React, Vite
- APIs: Java 25, Spring Boot 3.5
- AI Service: Python 3.11, FastAPI
- Containers: Docker, Docker Compose
- Cloud Target: AWS ECS/Fargate (artifacts included)
