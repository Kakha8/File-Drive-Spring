[CmdletBinding()]
param(
    [string]$AwsProfile = $env:AWS_PROFILE,
    [string]$Region = $env:AWS_REGION,
    [string]$PlanFile = "destroy.tfplan",
    [switch]$PlanOnly,
    # Retained so existing commands using -Apply continue to work. Destruction
    # is now the default after the explicit DESTROY confirmation.
    [switch]$Apply
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

function Get-AwsJson {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [switch]$AllowMissing
    )

    # Windows PowerShell 5.1 turns native stderr into a terminating
    # NativeCommandError when the script-wide preference is Stop. Capture the
    # process output with native-command errors temporarily non-terminating so
    # we can inspect the AWS CLI exit code and report its useful message.
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $commandLines = & aws @Arguments 2>&1
        $commandExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    $commandOutput = ($commandLines | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine
    if ($commandExitCode -ne 0) {
        if ($AllowMissing -and $commandOutput -match "NoSuchBucket|RepositoryNotFoundException|ResourceNotFoundException") {
            return $null
        }
        throw "AWS CLI command failed: aws $($Arguments -join ' ')`n$commandOutput"
    }

    if (-not $commandOutput.Trim()) {
        return $null
    }
    return $commandOutput | ConvertFrom-Json
}

function Remove-AllS3ObjectVersions {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Bucket,
        [Parameter(Mandatory = $true)]
        [string]$Profile,
        [Parameter(Mandatory = $true)]
        [string]$AwsRegion
    )

    Write-Host "Emptying S3 bucket '$Bucket'..."

    while ($true) {
        $response = Get-AwsJson -AllowMissing -Arguments @(
            "s3api", "list-object-versions",
            "--bucket", $Bucket,
            "--max-items", "1000",
            "--region", $AwsRegion,
            "--profile", $Profile,
            "--output", "json",
            "--no-cli-pager"
        )

        if ($null -eq $response) {
            Write-Host "Bucket '$Bucket' no longer exists; skipping."
            return
        }

        $objects = [System.Collections.Generic.List[object]]::new()
        foreach ($version in @($response.Versions)) {
            if ($null -ne $version) {
                $objects.Add([ordered]@{ Key = $version.Key; VersionId = $version.VersionId })
            }
        }
        foreach ($marker in @($response.DeleteMarkers)) {
            if ($null -ne $marker) {
                $objects.Add([ordered]@{ Key = $marker.Key; VersionId = $marker.VersionId })
            }
        }

        if ($objects.Count -eq 0) {
            Write-Host "Bucket '$Bucket' is empty."
            return
        }

        $deleteDocument = [ordered]@{
            Objects = $objects
            Quiet   = $true
        } | ConvertTo-Json -Depth 5 -Compress

        $temporaryFile = Join-Path ([IO.Path]::GetTempPath()) "file-drive-s3-delete-$([guid]::NewGuid()).json"
        try {
            [IO.File]::WriteAllText($temporaryFile, $deleteDocument, [Text.UTF8Encoding]::new($false))
            Invoke-CheckedCommand aws @(
                "s3api", "delete-objects",
                "--bucket", $Bucket,
                "--delete", "file://$temporaryFile",
                "--region", $AwsRegion,
                "--profile", $Profile,
                "--no-cli-pager"
            )
        }
        finally {
            Remove-Item -LiteralPath $temporaryFile -Force -ErrorAction SilentlyContinue
        }
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
    $Region = ((& aws configure get region --profile $AwsProfile) -join "").Trim()
    if ($LASTEXITCODE -ne 0 -or -not $Region) {
        $Region = "eu-central-1"
    }
}

Write-Host "Checking AWS identity using profile '$AwsProfile'..."
$accountId = ((& aws sts get-caller-identity --profile $AwsProfile --query Account --output text) -join "").Trim()
if ($LASTEXITCODE -ne 0 -or -not $accountId) {
    Write-Host "The SSO session is unavailable; opening AWS login..."
    Invoke-CheckedCommand aws @("sso", "login", "--profile", $AwsProfile)
    $accountId = ((& aws sts get-caller-identity --profile $AwsProfile --query Account --output text) -join "").Trim()
    if ($LASTEXITCODE -ne 0 -or -not $accountId) {
        throw "Could not verify the AWS identity after login."
    }
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$terraformDirectory = Join-Path $repositoryRoot "infra\preview"
if (-not (Test-Path -LiteralPath (Join-Path $terraformDirectory "versions.tf") -PathType Leaf)) {
    throw "Terraform preview configuration was not found at '$terraformDirectory'."
}
$planPath = if ([IO.Path]::IsPathRooted($PlanFile)) {
    $PlanFile
}
else {
    Join-Path $terraformDirectory $PlanFile
}

$bucketNames = @(
    "file-drive-preview-files-$accountId-$Region"
    "file-drive-preview-lockbox-$accountId-$Region"
    "file-drive-preview-quarantine-$accountId-$Region"
    "file-drive-preview-trash-$accountId-$Region"
)
$repositoryNames = @("file-drive-preview-api", "file-drive-preview-web")

Write-Host "AWS account: $accountId"
Write-Host "AWS region:  $Region"
Write-Host "Terraform:   $terraformDirectory"

$previousAwsProfile = $env:AWS_PROFILE
$previousAwsRegion = $env:AWS_REGION
$env:AWS_PROFILE = $AwsProfile
$env:AWS_REGION = $Region

Push-Location $terraformDirectory
try {
    Invoke-CheckedCommand terraform @("init")
    Invoke-CheckedCommand terraform @("plan", "-destroy", "-out=$planPath")

    if ($PlanOnly) {
        Write-Host "Destroy plan created but not applied: $planPath"
        Write-Host "Run this script without -PlanOnly when you are ready to permanently delete the preview data and resources."
        return
    }

    Write-Warning "This permanently deletes the preview database, uploaded files, container images, and AWS resources."
    $confirmation = Read-Host "Type DESTROY to continue"
    if ($confirmation -cne "DESTROY") {
        Write-Host "Destroy cancelled."
        return
    }

    foreach ($bucketName in $bucketNames) {
        Remove-AllS3ObjectVersions -Bucket $bucketName -Profile $AwsProfile -AwsRegion $Region
    }

    foreach ($repositoryName in $repositoryNames) {
        Write-Host "Deleting ECR repository '$repositoryName' and all images..."
        $null = Get-AwsJson -AllowMissing -Arguments @(
            "ecr", "delete-repository",
            "--repository-name", $repositoryName,
            "--force",
            "--region", $Region,
            "--profile", $AwsProfile,
            "--output", "json",
            "--no-cli-pager"
        )
    }

    # Do not reserve this disposable preview secret name for the normal
    # Secrets Manager recovery window; doing so prevents an immediate relaunch.
    Write-Host "Permanently deleting the disposable preview runtime secret..."
    $null = Get-AwsJson -AllowMissing -Arguments @(
        "secretsmanager", "delete-secret",
        "--secret-id", "file-drive/preview/api-runtime",
        "--force-delete-without-recovery",
        "--region", $Region,
        "--profile", $AwsProfile,
        "--output", "json",
        "--no-cli-pager"
    )

    # External cleanup changes AWS state, so discard the earlier plan and create a fresh one.
    Invoke-CheckedCommand terraform @("plan", "-destroy", "-out=$planPath")
    Invoke-CheckedCommand terraform @("apply", $planPath)
    Write-Host "Preview environment destroyed successfully. The bootstrap state bucket was not touched."
}
finally {
    Pop-Location
    $env:AWS_PROFILE = $previousAwsProfile
    $env:AWS_REGION = $previousAwsRegion
}
