# AWS ECS Deployment Notes

This folder contains ECS/Fargate task definition templates for the full platform, including frontend and API ingress.

## Prerequisites

- AWS CLI configured
- ECR repositories created for each service
- ECS cluster + VPC + subnets + security groups
- ALB for ingress routing
- RDS PostgreSQL instance or Aurora PostgreSQL compatible cluster
- S3 bucket for resume storage
- Cloud Map or equivalent internal service discovery for service-to-service DNS names

## Build and Push Images

From repository root:

1. Build images
   - docker build -t ai-talent-user-service:latest ./services/user-service
   - docker build -t ai-talent-candidate-service:latest ./services/candidate-service
   - docker build -t ai-talent-ai-ranking-service:latest ./services/ai-ranking-service
   - docker build -t ai-talent-notification-service:latest ./services/notification-service
   - docker build -t ai-talent-api-gateway:latest ./services/api-gateway
   - docker build -t ai-talent-frontend:latest ./frontend

2. Tag and push to ECR
   - aws ecr get-login-password --region <REGION> | docker login --username AWS --password-stdin <ACCOUNT_ID>.dkr.ecr.<REGION>.amazonaws.com
   - docker tag ai-talent-user-service:latest <ACCOUNT_ID>.dkr.ecr.<REGION>.amazonaws.com/ai-talent-user-service:latest
   - docker push <ACCOUNT_ID>.dkr.ecr.<REGION>.amazonaws.com/ai-talent-user-service:latest
   - Repeat for all services

## Register Task Definitions

- Update image URIs and secrets first
- Run:
  - aws ecs register-task-definition --cli-input-json file://deploy/aws-ecs/taskdef-user-service.json
  - aws ecs register-task-definition --cli-input-json file://deploy/aws-ecs/taskdef-candidate-service.json
  - aws ecs register-task-definition --cli-input-json file://deploy/aws-ecs/taskdef-ai-ranking-service.json
  - aws ecs register-task-definition --cli-input-json file://deploy/aws-ecs/taskdef-notification-service.json
   - aws ecs register-task-definition --cli-input-json file://deploy/aws-ecs/taskdef-api-gateway.json
   - aws ecs register-task-definition --cli-input-json file://deploy/aws-ecs/taskdef-frontend.json

## Create or Update ECS Services

- Create one ECS service per task definition
- Place `frontend` in public subnets behind an internet-facing ALB
- Place `api-gateway`, `user-service`, `candidate-service`, `notification-service`, and `ai-ranking-service` in private subnets
- Configure service discovery names such as `api-gateway.internal`, `user-service.internal`, `candidate-service.internal`, `notification-service.internal`, and `ai-ranking-service.internal`
- Point the frontend container's `API_GATEWAY_UPSTREAM` env var at `http://api-gateway.internal:8080`
- Point `candidate-service` at RDS for PostgreSQL and S3 for resume storage

## Production Hardening

- Store SMTP, JWT, and OpenAI secrets in AWS Secrets Manager
- Use S3 for resume storage
- Add RDS/DynamoDB for persistent multi-tenant data
- Add WAF and TLS certificates via ACM
