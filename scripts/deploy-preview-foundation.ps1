[CmdletBinding()]
param(
    [string]$AwsProfile = $env:AWS_PROFILE,
    [string]$Region = $env:AWS_REGION,
    [string]$AccountId,
    [string]$Repository = "file-drive-preview-api",
    [string]$ImageUri,
    [string]$WebRepository = "file-drive-preview-web",
    [string]$WebImageUri,
    [ValidateRange(0, 10)]
    [int]$DesiredCount = 1,
    [ValidateRange(0, 10)]
    [int]$WebDesiredCount = 1,
    [string]$PlanFile = "web-foundation.tfplan",
    [switch]$DeployWeb,
    [switch]$PlanOnly
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

foreach ($commandName in @("aws", "terraform")) {
    if (-not (Get-Command $commandName -ErrorAction SilentlyContinue)) {
        throw "Required command '$commandName' was not found in PATH."
    }
}

if (-not $AwsProfile) {
    $AwsProfile = "AdministratorAccess-678113404929"
}
if (-not $Region) {
    $Region = ((& aws configure get region --profile $AwsProfile) -join '').Trim()
    if ($LASTEXITCODE -ne 0 -or -not $Region) {
        $Region = "eu-central-1"
    }
}

Write-Host "Checking AWS identity using profile '$AwsProfile'..."
$detectedAccountId = ((& aws sts get-caller-identity `
    --profile $AwsProfile `
    --query Account `
    --output text) -join '').Trim()
if ($LASTEXITCODE -ne 0) {
    Write-Host "The SSO session is unavailable; opening AWS login..."
    Invoke-CheckedCommand aws @("sso", "login", "--profile", $AwsProfile)
    $detectedAccountId = ((& aws sts get-caller-identity `
        --profile $AwsProfile `
        --query Account `
        --output text) -join '').Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "Could not verify the AWS identity after login."
    }
}
if ($AccountId -and $AccountId -ne $detectedAccountId) {
    throw "Profile '$AwsProfile' belongs to account '$detectedAccountId', expected '$AccountId'."
}
$AccountId = $detectedAccountId

if (-not $ImageUri) {
    Write-Host "Detecting the newest tagged image in ECR repository '$Repository'..."
    $latestTag = ((& aws ecr describe-images `
        --repository-name $Repository `
        --region $Region `
        --profile $AwsProfile `
        --query "reverse(sort_by(imageDetails[?imageTags != null], &imagePushedAt))[0].imageTags[0]" `
        --output text) -join '').Trim()
    if ($LASTEXITCODE -ne 0 -or -not $latestTag -or $latestTag -eq "None") {
        throw "No tagged image was found in ECR repository '$Repository' in '$Region'."
    }
    $ImageUri = "$AccountId.dkr.ecr.$Region.amazonaws.com/$Repository`:$latestTag"
}

if ($DeployWeb -and -not $WebImageUri) {
    Write-Host "Detecting the newest tagged image in ECR repository '$WebRepository'..."
    $latestWebTag = ((& aws ecr describe-images `
        --repository-name $WebRepository `
        --region $Region `
        --profile $AwsProfile `
        --query "reverse(sort_by(imageDetails[?imageTags != null], &imagePushedAt))[0].imageTags[0]" `
        --output text) -join '').Trim()
    if ($LASTEXITCODE -ne 0 -or -not $latestWebTag -or $latestWebTag -eq "None") {
        throw "No tagged image was found in ECR repository '$WebRepository' in '$Region'."
    }
    $WebImageUri = "$AccountId.dkr.ecr.$Region.amazonaws.com/$WebRepository`:$latestWebTag"
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$terraformDirectory = Join-Path $repositoryRoot "infra\preview"
if (-not (Test-Path -LiteralPath (Join-Path $terraformDirectory "versions.tf") -PathType Leaf)) {
    throw "Terraform preview configuration was not found at '$terraformDirectory'."
}
if ([System.IO.Path]::IsPathRooted($PlanFile)) {
    $planPath = $PlanFile
}
else {
    $planPath = Join-Path $terraformDirectory $PlanFile
}

Write-Host "AWS account: $AccountId"
Write-Host "AWS region:  $Region"
Write-Host "API image:   $ImageUri"
Write-Host "Task count:  $DesiredCount"
if ($DeployWeb) {
    Write-Host "Web image:   $WebImageUri"
    Write-Host "Web tasks:   $WebDesiredCount"
}
Write-Host "Terraform:   $terraformDirectory"

$previousAwsProfile = $env:AWS_PROFILE
$previousAwsRegion = $env:AWS_REGION
$env:AWS_PROFILE = $AwsProfile
$env:AWS_REGION = $Region

Push-Location $terraformDirectory
try {
    Invoke-CheckedCommand terraform @("init")
    Invoke-CheckedCommand terraform @("fmt")
    Invoke-CheckedCommand terraform @("validate")
    $planArguments = @(
        "plan",
        "-var=container_image=$ImageUri",
        "-var=desired_count=$DesiredCount",
        "-out=$planPath"
    )
    if ($DeployWeb) {
        $planArguments += "-var=web_container_image=$WebImageUri"
        $planArguments += "-var=web_desired_count=$WebDesiredCount"
    }
    Invoke-CheckedCommand terraform $planArguments
    Invoke-CheckedCommand terraform @("show", $planPath)

    if ($PlanOnly) {
        Write-Host "Plan created but not applied: $planPath"
        return
    }

    $confirmation = Read-Host "Review the plan above. Type APPLY to execute it"
    if ($confirmation -cne "APPLY") {
        Write-Host "Apply cancelled. The saved plan remains at '$planPath'."
        return
    }

    Invoke-CheckedCommand terraform @("apply", $planPath)
    Write-Host "Preview foundation applied successfully."
    Invoke-CheckedCommand terraform @("output")
}
finally {
    Pop-Location
    $env:AWS_PROFILE = $previousAwsProfile
    $env:AWS_REGION = $previousAwsRegion
}
