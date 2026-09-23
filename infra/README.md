# Preview infrastructure

This is the first AWS foundation for File Drive: remote Terraform state, ECR,
networking, one ECS service, and a private Single-AZ PostgreSQL instance. The
`aws-preview` Spring profile allows a private ECS task to start and connect to
PostgreSQL. File and Lockbox operations remain disabled until S3 is migrated.
The task has no inbound rule, so it cannot be accessed from the public internet.

Run these commands yourself from local PowerShell. Terraform is intentionally
not run by Codex.

```powershell
$env:AWS_PROFILE = "AdministratorAccess-678113404929"
$env:AWS_REGION = "eu-central-1"
aws sts get-caller-identity
```

Confirm the returned account is `678113404929` before applying anything.

## 1. Bootstrap state storage

```powershell
cd infra/bootstrap
terraform init
terraform fmt -check
terraform validate
terraform plan -out bootstrap.tfplan
terraform apply bootstrap.tfplan
```

The bootstrap configuration uses local state. Keep its `terraform.tfstate`
secure and backed up; do not commit it. It creates a versioned, encrypted,
private S3 state bucket with deletion protection.

## 2. Preview environment

```powershell
cd ../preview
terraform init
terraform fmt -check
terraform validate
terraform plan -out preview.tfplan
terraform apply preview.tfplan
```

Review the plan before applying. This creates a billable RDS instance and
network resources. The ECS task has no inbound internet access. The database is
private. RDS manages the master password in Secrets Manager; Terraform does not
receive the database password.

After applying, run `terraform output` to see the ECR URL, ECS names, and
database endpoint. The service stays at zero tasks until you build and push an
image. The task definition receives the RDS password and generated admin/JWT
secrets from Secrets Manager. Those generated secrets are also present in
Terraform state, so protect access to the state bucket.

## Build and run the private preview task

First install the new Terraform provider and update the existing preview stack.
The plan should add generated application secrets and remove public ingress
from the ECS security group; it should not replace the database.

```powershell
cd infra/preview
terraform init
terraform fmt -check
terraform validate
terraform plan -out preview.tfplan
```

Review the plan. If it matches the changes described above, apply it and return
to the repository root:

```powershell
terraform apply preview.tfplan
cd ../..
```

From the repository root, run the helper. It refreshes the SSO session, checks
that the selected profile belongs to this AWS account, builds a Linux/amd64
image, and pushes it with a unique timestamp tag. ECR tags are immutable.

```powershell
$imageUri = .\scripts\push-preview-image.ps1
$imageUri
```

After the image push succeeds, update the ECS task image and start one task.
Replace the example URI with the URI printed by the script:

```powershell
cd infra/preview
terraform plan "-var=container_image=$imageUri" -var="desired_count=1" -out preview.tfplan
terraform apply preview.tfplan
```

Check the ECS service and CloudWatch logs for Spring startup and database
connection. There is no public API endpoint yet; add HTTPS through an ALB before
using browser login or sending credentials to the service. The preview profile
is for startup/database smoke testing only, not real file operations.

## Public web preview

The public preview adds an HTTP Application Load Balancer. `/api/*` is routed
to Spring and every other path is routed to the React application served by
Nginx. The preview cookie is HTTP-compatible; switch it back to Secure when
adding HTTPS. File and Lockbox operations still require the S3 migration.

The deployment is intentionally done in two phases because Terraform creates
the web ECR repository before the first web image can be pushed.

First create the load balancer, web repository, and updated API service while
leaving the web service at zero tasks:

The helper detects the active AWS account, configured region, and newest tagged
API image in ECR. It displays the saved plan and requires you to type `APPLY`:

```powershell
.\scripts\deploy-preview-foundation.ps1
```

To create and inspect the plan without applying it:

```powershell
.\scripts\deploy-preview-foundation.ps1 -PlanOnly
```

You can override detected values when necessary, for example:

```powershell
.\scripts\deploy-preview-foundation.ps1 `
  -AwsProfile "AdministratorAccess-678113404929" `
  -Region "eu-central-1" `
  -ImageUri "678113404929.dkr.ecr.eu-central-1.amazonaws.com/file-drive-preview-api:preview-20260923-012152-372"
```

The equivalent manual commands are:

```powershell
cd infra/preview
$imageUri = "678113404929.dkr.ecr.eu-central-1.amazonaws.com/file-drive-preview-api:preview-20260923-010738-180"
terraform init
terraform fmt
terraform validate
terraform plan "-var=container_image=$imageUri" -var="desired_count=1" -out web-foundation.tfplan
terraform show web-foundation.tfplan
terraform apply web-foundation.tfplan
cd ../..
```

Push the production React/Nginx image to the repository created above:

```powershell
$webImageUri = .\scripts\push-preview-image.ps1 -Component web
$webImageUri
```

Then start one web task with that immutable image:

The helper can detect both newest ECR images and deploy the web service:

```powershell
.\scripts\deploy-preview-foundation.ps1 -DeployWeb -PlanFile web-deploy.tfplan
```

The equivalent manual commands are:

```powershell
cd infra/preview
terraform plan `
  "-var=container_image=$imageUri" `
  -var="desired_count=1" `
  "-var=web_container_image=$webImageUri" `
  -var="web_desired_count=1" `
  -out web-deploy.tfplan
terraform show web-deploy.tfplan
terraform apply web-deploy.tfplan
terraform output -raw application_url
```
