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
