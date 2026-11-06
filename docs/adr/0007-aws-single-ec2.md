# ADR 0007: Deploy to one EC2 instance with Docker Compose

**Date:** 2026-11-05 · **Status:** Accepted

## Context
The plan asks for an AWS deployment (ECR, ECS Fargate or EC2, Secrets Manager, CloudWatch) on a budget of
$0–10 in total, with a billing alarm at $5. Five JVM services plus MySQL need about 3.5 GB of RAM. On ECS Fargate
with an ALB and RDS, that costs roughly $100 or more a month while running. That leaves the open question
"ECS Fargate or one EC2 with Compose?".

## Decision
- **One EC2 t3.medium** (4 GB) in ap-southeast-2 runs `infra/aws/docker-compose.prod.yml`. Memory limits fit
  5 JVMs and MySQL; only the gateway is exposed (port 80); there's no SSH (Session Manager instead).
- **ECR** stores the images, tagged with the git commit SHA, so every deploy is traceable and easy to roll back.
- **Secrets Manager**: one JSON secret, `shiptrack/prod`. CloudFormation generates the JWT secret; the first deploy
  generates the remaining passwords and saves them to the same secret. At $0.40 per secret per month, one secret
  instead of five matters on this budget.
- **CloudWatch Logs** through Docker's `awslogs` driver, kept for 7 days.
- **GitHub Actions** deploys only from `main`, after CI passes, and rolls the instance over with SSM Run Command.
  It logs in with keyless OIDC where allowed. AWS Free plan "project" accounts block creating the OIDC provider
  (organization SCP), so the default is a deploy-only IAM user whose key lives only in GitHub Secrets. Both use the
  same least-privilege policy (push to the 5 ECR repos, SSM on one instance).
- Everything is in one CloudFormation template (`infra/aws/shiptrack.yml`), including an AWS Budget at $5.
- MySQL runs in a container on the instance's encrypted EBS volume, not RDS, to keep costs down.

## Consequences
- About $0.06/hour while running and about $2/month while stopped, which fits the budget if the instance is stopped between demos.
- Single point of failure, and deploys cause a brief restart. That's fine for a portfolio demo, not for real customers.
- The public URL changes after a stop/start. An Elastic IP would fix this but costs about $3.60/month.
- The path to ECS is short: the same images and environment variables; replace the Compose file with task definitions,
  RDS and an ALB.
