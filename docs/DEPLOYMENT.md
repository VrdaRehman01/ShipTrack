# Deploying ShipTrack to AWS

One EC2 instance runs the same Docker Compose stack you run locally (see [ADR 0007](adr/0007-aws-single-ec2.md)).
After the one-time setup below, **every merge to `main` deploys automatically**: CI passes, then the
Deploy workflow builds the images, pushes them to ECR and updates the instance through SSM.

| What | Where |
|---|---|
| Images | ECR: `shiptrack/<service>` (last 5 kept) |
| Secrets | Secrets Manager: `shiptrack/prod` (one JSON secret, generated, never in Git) |
| Logs | CloudWatch Logs: `/shiptrack/prod` (7 days) |
| Server | EC2 `shiptrack-prod` (c7i-flex.large: 2 vCPU / 4 GB, free-tier eligible; Amazon Linux 2023), port 80 only, no SSH |
| Cost guardrail | AWS Budget `shiptrack-monthly`: email at 80% of $5 and when forecast exceeds $5 |

**Cost:** about **$0.06/hour while running** (≈ $1.50 for a full day) and **≈ $2/month while stopped**
(disk + secret). Stop the instance when you're not demoing. New AWS accounts also get free-tier credits.

---

## One-time setup (≈ 15 minutes, all in the browser)

### 0. Secure the account
In the AWS console: top-right account menu → **Security credentials** → **Assign MFA device** for the root user.
Don't create access keys for root. You won't need any keys at all with the steps below.

### 1. Open CloudShell in Sydney
Check the browser address bar contains `region=ap-southeast-2` (Sydney). Then click **CloudShell** in the
bottom-left corner of the console (or the `>_` icon in the top bar). It's a terminal that is already logged in to your account.

### 2. Upload the template
In CloudShell: **Actions → Upload file** → choose `infra/aws/shiptrack.yml` from this repo.

### 3. Create the stack
Replace the email with yours (it receives budget alerts):
```bash
aws cloudformation deploy   --stack-name shiptrack   --template-file shiptrack.yml   --capabilities CAPABILITY_NAMED_IAM   --parameter-overrides AlertEmail=you@example.com
```
It takes about 3–5 minutes. AWS then emails you to **confirm the budget alert subscription**. Click the link.

> **Free plan "project" accounts** block keyless GitHub sign-in (OIDC) with an organization policy, so the default
> is `GitHubAuthMode=accessKey`: a deploy-only IAM user. On a normal account you can use
> `GitHubAuthMode=oidc` instead (no keys at all) and set the `AWS_DEPLOY_ROLE_ARN` variable in step 5.

If the deploy fails, list the reasons, then delete the failed stack before retrying:
```bash
aws cloudformation describe-stack-events --stack-name shiptrack   --query "StackEvents[?contains(ResourceStatus,'FAILED')].[LogicalResourceId,ResourceStatusReason]" --output table
aws cloudformation delete-stack --stack-name shiptrack
```

### 4. Read the outputs
```bash
aws cloudformation describe-stacks --stack-name shiptrack   --query "Stacks[0].Outputs[].[OutputKey,OutputValue]" --output table
```

### 5. Connect GitHub to AWS
**a) Create the deploy user's key** in CloudShell:
```bash
aws iam create-access-key --user-name shiptrack-github-deploy   --query "AccessKey.[AccessKeyId,SecretAccessKey]" --output text
```
It prints two values: the key ID (starts with `AKIA`) and the secret key. **Don't share them with anyone, including
in chats.** Put them straight into GitHub, then run `clear` in CloudShell.

**b) GitHub:** repo → **Settings → Secrets and variables → Actions**.
- **Secrets** tab → **New repository secret**, twice:

  | Name | Value |
  |---|---|
  | `AWS_ACCESS_KEY_ID` | the first value (`AKIA…`) |
  | `AWS_SECRET_ACCESS_KEY` | the second value |

- **Variables** tab → **New repository variable**:

  | Name | Value |
  |---|---|
  | `AWS_REGION` | `ap-southeast-2` |

  The deploy finds the server by its name tag (`shiptrack-prod`), so no instance ID is needed. That also keeps
  working if the server is ever replaced.

The deploy user can only push ShipTrack images and run commands on this one instance. If its key ever leaks,
delete it with `aws iam delete-access-key --user-name shiptrack-github-deploy --access-key-id AKIA...` and create a new one.

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

## Updating the AWS stack
When `infra/aws/shiptrack.yml` changes (for example, the website added the `shiptrack/frontend` image repository),
update the stack **before** merging the change, so the deploy has everything it needs. In CloudShell:
```bash
rm -f shiptrack.yml          # CloudShell won't overwrite uploads
# Actions → Upload file → infra/aws/shiptrack.yml, then:
aws cloudformation deploy --stack-name shiptrack --template-file shiptrack.yml   --capabilities CAPABILITY_NAMED_IAM --parameter-overrides AlertEmail=you@example.com
```
Only the differences are applied. The server is kept unless the change touches the server itself.
**Before you update, check the change set** if you have data you care about. Instance replacements show `Replacement: True`:
```bash
aws cloudformation deploy ... --no-execute-changeset    # then follow the printed describe-change-set command
```
The database lives on the server's disk, so a replaced server starts with an empty database (the passwords in
Secrets Manager are kept).

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
| Deploy workflow is skipped | The `AWS_REGION` variable from step 5 isn't set |
| `The security token included in the request is invalid` | The `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` secrets are wrong; create a new key (step 5a) |
| `Not authorized to perform sts:AssumeRoleWithWebIdentity` (oidc mode) | The workflow didn't run from `main`, or `GitHubRepo` in the stack doesn't match your repo |
| Deploy step times out | First boot installs Docker; wait 2 minutes after the stack is created, then re-run. Check the deploy output in CloudWatch |
| Site unreachable | Instance stopped, or the URL changed after a restart (step "Start again") |
