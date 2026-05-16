# Render Deployment Guide

This folder contains deployment configuration for Render, a modern cloud platform that simplifies deploying containerized applications.

**For Render vs AWS comparison, see [RENDER-vs-AWS.md](../../RENDER-vs-AWS.md).**

## Quick Start

- Render account (https://render.com)
- GitHub repository with this code pushed
- AWS S3 bucket for resume storage (optional, but recommended for production)
- SendGrid account for SMTP (or any other email service)

## One-Click Deployment via render.yaml

1. Connect your GitHub repo to Render:
   - Go to https://dashboard.render.com
   - Click "New +" and select "Blueprint"
   - Connect your GitHub account and select this repository
   - Render will auto-detect the `render.yaml` file

2. Configure environment variables in Render dashboard:
   - `JWT_SECRET`: Use a strong random value (e.g., `openssl rand -base64 32`)
   - `DB_USER`: Render auto-creates with a secure default
   - `DB_PASSWORD`: Render auto-creates with a secure default
   - `STORAGE_S3_BUCKET`: Your S3 bucket name
   - `AWS_ACCESS_KEY_ID`: IAM user credentials with S3 access
   - `AWS_SECRET_ACCESS_KEY`: IAM user secret
   - `SMTP_PASSWORD`: SendGrid API key

3. Deploy:
   - Click "Create Blueprint"
   - Render will provision PostgreSQL, build all services, and deploy them
   - Each service gets a unique `*.onrender.com` URL

## Manual Service-by-Service Setup (Alternative)

If you prefer to create services manually:

### 1. Create PostgreSQL Database
- Dashboard -> New Database -> PostgreSQL
- Plan: Standard tier (recommended for production)
- Name: `talent_platform`
- Note the connection credentials

### 2. Create Backend Services
For each service (user-service, candidate-service, ai-ranking-service, notification-service, api-gateway):

- New Web Service -> Connect GitHub repo
- Runtime: Docker
- Build Command: (leave empty; Dockerfile will handle)
- Start Command: (leave empty; Dockerfile will handle)
- Plan: Starter (for development) or Standard (for production)
- Advanced: Enable "Auto-Deploy on Push"
- Environment variables: Set as documented above
- Internal services: Mark `user-service`, `candidate-service`, `notification-service`, `ai-ranking-service` as private if desired (not exposed to internet)

### 3. Create Static Frontend
- New Static Site
- Build Command: `cd frontend && npm install && npm run build`
- Publish Directory: `frontend/dist`
- Environment: `VITE_API_BASE=/api` (uses same-origin proxy)
- Advanced: Add redirect rules:
   - Path: `/api/*` -> Destination: `https://api-gateway.onrender.com/*` (replace with actual API gateway URL)
   - Path: `/*` -> Destination: `/index.html` (SPA routing)

### 4. Point API Gateway to Services
Update environment variables on the api-gateway service:
- `USER_SERVICE_INTERNAL_URL=https://user-service.onrender.com`
- `CANDIDATE_SERVICE_INTERNAL_URL=https://candidate-service.onrender.com`
- `NOTIFICATION_SERVICE_INTERNAL_URL=https://notification-service.onrender.com`

And on candidate-service:
- `AI_SERVICE_URL=https://ai-ranking-service.onrender.com`
- `NOTIFICATION_SERVICE_URL=https://notification-service.onrender.com`

## Cost Estimates

- **Starter Plan Web Services** (4 services): ~$7/month per service = $28/month
- **Standard Plan Web Service** (1 service for candidate-service): ~$12/month
- **PostgreSQL Standard Tier**: ~$30/month
- **Static Site (Frontend)**: Free
- **Total estimate**: ~$70-75/month

## S3 Storage Configuration

Resume files are stored in S3. To configure:

1. Create S3 bucket in AWS (e.g., `ai-talent-platform-resumes`)
2. Create IAM user with S3 permissions only:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Action": ["s3:GetObject", "s3:PutObject"],
         "Resource": "arn:aws:s3:::ai-talent-platform-resumes/*"
       },
       {
         "Effect": "Allow",
         "Action": ["s3:ListBucket"],
         "Resource": "arn:aws:s3:::ai-talent-platform-resumes"
       }
     ]
   }
   ```
3. Set environment variables on candidate-service:
   - `STORAGE_PROVIDER=s3`
   - `STORAGE_S3_BUCKET=ai-talent-platform-resumes`
   - `AWS_ACCESS_KEY_ID=<IAM_ACCESS_KEY>`
   - `AWS_SECRET_ACCESS_KEY=<IAM_SECRET_KEY>`

## Email (SMTP) Configuration

The platform uses SendGrid for sending interview invitations:

1. Create SendGrid account: https://sendgrid.com
2. Generate API key (Settings -> API Keys)
3. Set environment variables on notification-service:
   - `SMTP_PASSWORD=<SENDGRID_API_KEY>`
   - `SMTP_FROM=noreply@your-domain.com` (SendGrid verified sender)

## Monitoring & Logs

- Each Render service has live logs in the dashboard
- PostgreSQL metrics are available in the database dashboard
- Failed deployments trigger email notifications

## Database Backups

Render automatically backs up PostgreSQL daily. To restore:
- Dashboard -> Databases -> Select `talent_platform`
- Backups tab -> Select backup -> Restore

## Environment-Specific Configuration

### Development
- Use `render.yaml` with Starter tier plans
- Disable SMTP (uses logging fallback)
- Use local S3-compatible storage (e.g., MinIO) if needed

### Production
- Scale to Standard tier plans
- Enable SendGrid SMTP
- Use AWS S3 for resume storage
- Set strong JWT_SECRET values
- Configure custom domain (Render supports custom domains)
- Enable auto-scaling if available on plan

## Troubleshooting

**Services can't reach each other:**
- Verify internal service URLs in environment variables match the Render service names
- Check that services are in the same "region" in render.yaml

**Database connection fails:**
- Verify DB connection string includes `sslmode=require` for Render PostgreSQL

**S3 uploads fail:**
- Check IAM user has s3:PutObject permission
- Verify bucket name matches STORAGE_S3_BUCKET env var
- Check AWS credentials are URL-encoded if they contain special characters

**Email not sending:**
- Verify SendGrid API key is set
- Check SMTP_FROM matches a SendGrid verified sender
- Review notification-service logs for SMTP errors
