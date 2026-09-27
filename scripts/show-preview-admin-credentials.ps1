[CmdletBinding()]
param(
    [string]$AwsProfile = $env:AWS_PROFILE,
    [string]$Region = $env:AWS_REGION,
    [string]$ExpectedAccountId = "678113404929"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command aws -ErrorAction SilentlyContinue)) {
    throw "Required command 'aws' was not found in PATH."
}
if (-not $AwsProfile) {
    $AwsProfile = "AdministratorAccess-678113404929"
}
if (-not $Region) {
    $Region = "eu-central-1"
}

function Get-AwsCommandOutput {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    # Keep native stderr from becoming an unhelpful terminating error in
    # Windows PowerShell 5.1. The AWS CLI exit code is checked explicitly.
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $lines = & aws @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    $output = ($lines | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine
    if ($exitCode -ne 0) {
        throw "AWS CLI command failed: aws $($Arguments -join ' ')`n$output"
    }
    return $output.Trim()
}

try {
    $accountId = Get-AwsCommandOutput @(
        "sts", "get-caller-identity",
        "--profile", $AwsProfile,
        "--query", "Account",
        "--output", "text"
    )
}
catch {
    Write-Host "The SSO session is unavailable; opening AWS login..."
    & aws sso login --profile $AwsProfile
    if ($LASTEXITCODE -ne 0) {
        throw "AWS SSO login failed for profile '$AwsProfile'."
    }
    $accountId = Get-AwsCommandOutput @(
        "sts", "get-caller-identity",
        "--profile", $AwsProfile,
        "--query", "Account",
        "--output", "text"
    )
}

if ($accountId -ne $ExpectedAccountId) {
    throw "Profile '$AwsProfile' belongs to account '$accountId', expected '$ExpectedAccountId'."
}

$secretJson = Get-AwsCommandOutput @(
    "secretsmanager", "get-secret-value",
    "--secret-id", "file-drive/preview/api-runtime",
    "--region", $Region,
    "--profile", $AwsProfile,
    "--query", "SecretString",
    "--output", "text",
    "--no-cli-pager"
)
$secret = $secretJson | ConvertFrom-Json
if (-not $secret.ADMIN_PASSWORD) {
    throw "The preview secret does not contain ADMIN_PASSWORD."
}

Write-Host "Preview administrator credentials:"
Write-Host "Username: admin"
Write-Host "Password: $($secret.ADMIN_PASSWORD)"
Write-Warning "The preview currently uses HTTP. Do not reuse this password anywhere else."
