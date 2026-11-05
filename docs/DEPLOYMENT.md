# Deploying ShipTrack to AWS

One EC2 instance runs the same Docker Compose stack you run locally (see [ADR 0007](adr/0007-aws-single-ec2.md)).
After the one-time setup below, **every merge to `main` deploys automatically**: CI passes, then the
Deploy workflow builds the images, pushes them to ECR and updates the instance through SSM.

| What | Where |
|---|---|
| Images | ECR: `shiptrack/<service>` (last 5 kept) |
| Secrets | Secrets Manager: `shiptrack/prod` (one JSON secret, generated, never in Git) |
| Logs | CloudWatch Logs: `/shiptrack/prod` (7 days) |
| Server | EC2 `shiptrack-prod` (t3.medium, Amazon Linux 2023), port 80 only, no SSH |
| Cost guardrail | AWS Budget `shiptrack-monthly`: email at 80% of $5 and when forecast exceeds $5 |

**Cost:** about **$0.06/hour while running** (≈ $1.50 for a full day) and **≈ $2/month while stopped**
(disk + secret). Stop the instance when you're not demoing. New AWS accounts also get free-tier credits.

---

## One-time setup (≈ 15 minutes, all in the browser)

### 0. Secure the account
In the AWS console: top-right account menu → **Security credentials** → **Assign MFA device** for the root user.
Don't create access keys for root. You won't need any keys at all with the steps below.

### 1. Open CloudShell in Sydney
Make sure the region (top right) is **Asia Pacific (Sydney) ap-southeast-2**, then click the **CloudShell**
icon (`>_`) in the top bar. It's a terminal that is already logged in to your account.

### 2. Upload the template
In CloudShell: **Actions → Upload file** → choose `infra/aws/shiptrack.yml` from this repo.

### 3. Create the stack
Replace the email with yours (it receives budget alerts):
```bash
aws cloudformation deploy \
  --stack-name shiptrack \
  --template-file shiptrack.yml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides AlertEmail=you@example.com
```
It takes about 3 minutes. AWS then emails you to **confirm the budget alert subscription**. Click the link.

> If it fails with "Provider with url https://token.actions.githubusercontent.com already exists",
> add `CreateGitHubOidcProvider=false` to `--parameter-overrides`.

### 4. Read the outputs
```bash
aws cloudformation describe-stacks --stack-name shiptrack \
  --query "Stacks[0].Outputs[].[OutputKey,OutputValue]" --output table
```

### 5. Connect GitHub to AWS
On GitHub: repo → **Settings → Secrets and variables → Actions → Variables tab → New repository variable**.
Create three **variables** (not secrets; none of these are sensitive):

| Name | Value (from step 4) |
|---|---|
| `AWS_DEPLOY_ROLE_ARN` | `DeployRoleArn` |
| `AWS_INSTANCE_ID` | `InstanceId` |
| `AWS_REGION` | `ap-southeast-2` |

GitHub logs in to AWS with short-lived OIDC tokens, and only from the `main` branch. No AWS keys are stored in GitHub.

### 6. Deploy
Merge your pull request into `main`. CI runs, and when it's green the **Deploy** workflow starts by itself.
You can also start it by hand: **Actions → Deploy → Run workflow** (on `main`).
The first deploy takes about 10 minutes (it builds 5 images). When it finishes, the workflow summary shows the URL.

### 7. Log in as admin
In CloudShell:
```bash
aws secretsmanager get-secret-value --secret-id shiptrack/prod \
  --query SecretString --output text | jq -r .adminPassword
```
The admin email is `admin@shiptrack.demo` (stack parameter `AdminEmail`).
Open `http://<ApiUrl>/swagger-ui.html` and log in, or run the Postman collection with `baseUrl=http://<ApiUrl>`.

---

## Day-to-day

**Stop when not demoing** (saves ~95% of the cost):
```bash
aws ec2 stop-instances --instance-ids <InstanceId>
```
**Start again** (the stack restarts by itself; the **public URL changes** after a stop/start):
```bash
aws ec2 start-instances --instance-ids <InstanceId>
aws ec2 wait instance-status-ok --instance-ids <InstanceId>
aws ec2 describe-instances --instance-ids <InstanceId> \
  --query "Reservations[0].Instances[0].PublicDnsName" --output text
```
Deploys fail fast with a clear message if the instance is stopped. Start it, then re-run the workflow.

**Logs:** CloudWatch → Log groups → `/shiptrack/prod` (one stream per container, plus deploy output).

**Shell on the server** (no SSH needed): EC2 → Instances → `shiptrack-prod` → **Connect → Session Manager**.
```bash
sudo -i; cd /opt/shiptrack
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs --tail=100 shipment-service
```

## Tear everything down
```bash
aws cloudformation delete-stack --stack-name shiptrack
aws cloudformation wait stack-delete-complete --stack-name shiptrack
```
This deletes the instance, its disk (including the database), the images and the logs. Secrets Manager keeps the
secret recoverable for 7–30 days; to remove it immediately, run
`aws secretsmanager delete-secret --secret-id shiptrack/prod --force-delete-without-recovery`.

## Troubleshooting
| Problem | Fix |
|---|---|
| Deploy workflow is skipped | The three repo variables from step 5 aren't set |
| `Not authorized to perform sts:AssumeRoleWithWebIdentity` | The workflow didn't run from `main`, or `GitHubRepo` in the stack doesn't match your repo |
| Deploy step times out | First boot installs Docker; wait 2 minutes after the stack is created, then re-run. Check the deploy output in CloudWatch |
| Site unreachable | Instance stopped, or the URL changed after a restart (step "Start again") |
