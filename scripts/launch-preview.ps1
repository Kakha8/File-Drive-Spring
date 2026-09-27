[CmdletBinding()]
param(
    [string]$AwsProfile = $env:AWS_PROFILE,
    [string]$Region = $env:AWS_REGION,
    [string]$ExpectedAccountId = "678113404929"
)

$ErrorActionPreference = "Stop"

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command,
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code $LASTEXITCODE`: $Command $($Arguments -join ' ')"
    }
}

foreach ($commandName in @("aws", "docker", "terraform")) {
    if (-not (Get-Command $commandName -ErrorAction SilentlyContinue)) {
        throw "Required command '$commandName' was not found in PATH."
    }
}

if (-not $AwsProfile) {
    $AwsProfile = "AdministratorAccess-678113404929"
}
if (-not $Region) {
    $Region = "eu-central-1"
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$terraformDirectory = Join-Path $repositoryRoot "infra\preview"
$foundationPlan = Join-Path $terraformDirectory "preview-create.tfplan"

Write-Host "Checking AWS identity using profile '$AwsProfile'..."
$accountId = ((& aws sts get-caller-identity `
    --profile $AwsProfile `
    --query Account `
    --output text) -join "").Trim()
if ($LASTEXITCODE -ne 0 -or -not $accountId) {
    Write-Host "The SSO session is unavailable; opening AWS login..."
    Invoke-CheckedCommand aws @("sso", "login", "--profile", $AwsProfile)
    $accountId = ((& aws sts get-caller-identity `
        --profile $AwsProfile `
        --query Account `
        --output text) -join "").Trim()
}
if ($accountId -ne $ExpectedAccountId) {
    throw "Profile '$AwsProfile' belongs to account '$accountId', expected '$ExpectedAccountId'."
}

Write-Host "Checking Docker engine..."
Invoke-CheckedCommand docker @("info")

$previousAwsProfile = $env:AWS_PROFILE
$previousAwsRegion = $env:AWS_REGION
$env:AWS_PROFILE = $AwsProfile
$env:AWS_REGION = $Region

try {
    Write-Host "Planning the AWS preview foundation..."
    Push-Location $terraformDirectory
    try {
        Invoke-CheckedCommand terraform @("init")

        # A normal Secrets Manager deletion reserves the secret name for its
        # recovery window. If this preview was recently destroyed, restore and
        # import that secret so Terraform can safely manage it again.
        $runtimeSecretName = "file-drive/preview/api-runtime"
        $runtimeSecretArn = ((& aws secretsmanager list-secrets `
            --include-planned-deletion `
            --region $Region `
            --profile $AwsProfile `
            --query "SecretList[?Name=='$runtimeSecretName'].ARN | [0]" `
            --output text) -join "").Trim()
        if ($LASTEXITCODE -ne 0) {
            throw "Could not inspect the preview runtime secret."
        }
        if ($runtimeSecretArn -and $runtimeSecretArn -ne "None") {
            $deletedDate = ((& aws secretsmanager describe-secret `
                --secret-id $runtimeSecretArn `
                --region $Region `
                --profile $AwsProfile `
                --query DeletedDate `
                --output text) -join "").Trim()
            if ($LASTEXITCODE -ne 0) {
                throw "Could not inspect the preview runtime secret deletion status."
            }
            if ($deletedDate -and $deletedDate -ne "None") {
                Write-Host "Restoring the preview runtime secret from its deletion recovery window..."
                Invoke-CheckedCommand aws @(
                    "secretsmanager", "restore-secret",
                    "--secret-id", $runtimeSecretArn,
                    "--region", $Region,
                    "--profile", $AwsProfile
                )
            }

            $managedResources = @(& terraform state list)
            if ($LASTEXITCODE -ne 0) {
                throw "Could not inspect Terraform state."
            }
            if ($managedResources -notcontains "aws_secretsmanager_secret.api_runtime") {
                Write-Host "Importing the recovered preview runtime secret into Terraform state..."
                Invoke-CheckedCommand terraform @(
                    "import",
                    "aws_secretsmanager_secret.api_runtime",
                    $runtimeSecretArn
                )
            }
        }

        Invoke-CheckedCommand terraform @("fmt", "-check")
        Invoke-CheckedCommand terraform @("validate")
        Invoke-CheckedCommand terraform @(
            "plan",
            "-var=desired_count=0",
            "-var=web_desired_count=0",
            "-out=$foundationPlan"
        )
        Invoke-CheckedCommand terraform @("show", $foundationPlan)

        $confirmation = Read-Host "Review the foundation plan above. Type CREATE to apply it"
        if ($confirmation -cne "CREATE") {
            Write-Host "Launch cancelled. Nothing was applied."
            return
        }

        Invoke-CheckedCommand terraform @("apply", $foundationPlan)
    }
    finally {
        Pop-Location
    }

    Write-Host "Building and pushing the API image..."
    $apiImage = (& (Join-Path $PSScriptRoot "push-preview-image.ps1") `
        -Component api `
        -Profile $AwsProfile `
        -Region $Region `
        -AccountId $accountId | Select-Object -Last 1)
    if (-not $apiImage) {
        throw "The API image helper did not return an image URI."
    }

    Write-Host "Building and pushing the web image..."
    $webImage = (& (Join-Path $PSScriptRoot "push-preview-image.ps1") `
        -Component web `
        -Profile $AwsProfile `
        -Region $Region `
        -AccountId $accountId | Select-Object -Last 1)
    if (-not $webImage) {
        throw "The web image helper did not return an image URI."
    }

    Write-Host "Deploying the API and web services..."
    & (Join-Path $PSScriptRoot "deploy-preview-foundation.ps1") `
        -AwsProfile $AwsProfile `
        -Region $Region `
        -AccountId $accountId `
        -ImageUri $apiImage `
        -WebImageUri $webImage `
        -DeployWeb `
        -PlanFile "preview-start.tfplan"

    if ($LASTEXITCODE -ne 0) {
        throw "Preview deployment failed with exit code $LASTEXITCODE."
    }

    Push-Location $terraformDirectory
    try {
        $applicationUrl = ((& terraform output -raw application_url) -join "").Trim()
        if ($LASTEXITCODE -ne 0) {
            throw "Could not read the application URL from Terraform."
        }
    }
    finally {
        Pop-Location
    }

    Write-Host "Preview launch finished. ECS and ClamAV may need several minutes to become healthy."
    Write-Host "Application URL: $applicationUrl"
}
finally {
    $env:AWS_PROFILE = $previousAwsProfile
    $env:AWS_REGION = $previousAwsRegion
}
