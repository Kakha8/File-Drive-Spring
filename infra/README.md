# Preview infrastructure

This is the first AWS foundation for File Drive: remote Terraform state, ECR,
networking, one ECS service, and a private Single-AZ PostgreSQL instance. The
ECS service starts with **zero tasks** because the application still needs a
PostgreSQL/AWS runtime profile before it can run without local MinIO, ClamAV,
and TLS files.

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

Find your current public IPv4 address and set it as a `/32` CIDR. For example,
replace the placeholder below with your own address:

```powershell
cd ../preview
terraform init
terraform fmt -check
terraform validate
terraform plan -var="allowed_api_cidr=203.0.113.10/32" -out preview.tfplan
terraform apply preview.tfplan
```

Review the plan before applying. This creates a billable RDS instance and
network resources. The API ingress rule allows port 8443 only from your IP.
The database is private. RDS manages the master password in Secrets Manager;
Terraform does not receive the password.

After applying, run `terraform output` to see the ECR URL, ECS names, and
database endpoint. The service stays at zero tasks until the app supports this
environment. The next implementation step is the AWS runtime profile and an
image deployment workflow; only then set `desired_count` to one.
