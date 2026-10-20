[CmdletBinding()]
param(
    [ValidateSet("api", "web")]
    [string]$Component = "api",
    [string]$Profile = "AdministratorAccess-678113404929",
    [string]$Region = "eu-central-1",
    [string]$AccountId = "678113404929",
    [string]$Repository,
    [string]$Tag = ("preview-" + (Get-Date -Format "yyyyMMdd-HHmmss-fff")),
    [switch]$LoginOnly
)

$ErrorActionPreference = "Stop"

foreach ($commandName in @("aws", "docker")) {
    if (-not (Get-Command $commandName -ErrorAction SilentlyContinue)) {
        throw "Required command '$commandName' was not found in PATH."
    }
}
if ($Tag -notmatch '^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$') {
    throw "Tag '$Tag' is not a valid ECR image tag."
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
if (-not $Repository) {
    $Repository = "file-drive-preview-$Component"
}
if ($Component -eq "web") {
    $buildContext = Join-Path $repositoryRoot "frontend"
    $dockerfilePath = Join-Path $buildContext "Dockerfile"
}
else {
    $buildContext = $repositoryRoot
    $dockerfilePath = Join-Path $repositoryRoot "Dockerfile"
}
if (-not (Test-Path -LiteralPath $dockerfilePath -PathType Leaf)) {
    throw "Dockerfile was not found at '$dockerfilePath'."
}

$registry = "$AccountId.dkr.ecr.$Region.amazonaws.com"
$imageUri = "$registry/$Repository`:$Tag"
$localImage = "file-drive-preview-$Component`:$Tag"

Write-Host "Checking AWS SSO session for profile '$Profile'..."
$activeAccount = & aws sts get-caller-identity --profile $Profile --query Account --output text
if ($LASTEXITCODE -ne 0) {
    Write-Host "Refreshing AWS SSO session for profile '$Profile'..."
    & aws sso login --profile $Profile | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "AWS SSO login failed for profile '$Profile'."
    }
    $activeAccount = & aws sts get-caller-identity --profile $Profile --query Account --output text
    if ($LASTEXITCODE -ne 0) {
        throw "Could not verify the AWS identity for profile '$Profile' after login."
    }
}
if ($activeAccount.Trim() -ne $AccountId) {
    throw "Profile '$Profile' is authenticated to account '$($activeAccount.Trim())', expected '$AccountId'."
}

$repositoryUri = & aws ecr describe-repositories `
    --repository-names $Repository `
    --region $Region `
    --profile $Profile `
    --query 'repositories[0].repositoryUri' `
    --output text
if ($LASTEXITCODE -ne 0) {
    throw "ECR repository '$Repository' was not found in '$Region'. Apply the preview Terraform configuration first."
}
if ($repositoryUri -notlike "$registry/*") {
    throw "ECR returned an unexpected repository URI: '$repositoryUri'."
}

Write-Host "Checking Docker engine..."
& docker info | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "Docker is unavailable. Start Docker Desktop and retry."
}

Write-Host "Preparing an isolated Docker credential for $registry..."
$authorizationToken = ((& aws ecr get-authorization-token `
    --region $Region `
    --profile $Profile `
    --query 'authorizationData[0].authorizationToken' `
    --output text) -join '').Trim()
if ($LASTEXITCODE -ne 0) {
    throw "Could not get an ECR authorization token."
}
$decodedAuthorization = [Text.Encoding]::UTF8.GetString(
    [Convert]::FromBase64String($authorizationToken)
)
$credentialSeparator = $decodedAuthorization.IndexOf(':')
if ($credentialSeparator -lt 1) {
    throw "ECR returned an invalid authorization token."
}
$ecrUsername = $decodedAuthorization.Substring(0, $credentialSeparator)
$ecrPassword = $decodedAuthorization.Substring($credentialSeparator + 1)
if ($ecrUsername -ne "AWS") {
    throw "ECR returned the unexpected Docker username '$ecrUsername'."
}

# Avoid `docker login --password-stdin` here. Windows PowerShell 5.1 writes a
# BOM to redirected process input on some .NET Framework versions, corrupting
# both the ECR password and Docker Desktop credential-helper JSON. An isolated
# DOCKER_CONFIG gives Docker the same short-lived token without stdin encoding.
$temporaryDockerConfig = Join-Path `
    ([IO.Path]::GetTempPath()) `
    "file-drive-docker-config-$([guid]::NewGuid())"
$previousDockerConfig = $env:DOCKER_CONFIG
[void](New-Item -ItemType Directory -Path $temporaryDockerConfig)
$temporaryDockerConfigFile = Join-Path $temporaryDockerConfig "config.json"

try {
    $basicCredential = [Convert]::ToBase64String(
        [Text.Encoding]::ASCII.GetBytes("$ecrUsername`:$ecrPassword")
    )
    $dockerConfigJson = @{
        auths = @{
            $registry = @{ auth = $basicCredential }
        }
    } | ConvertTo-Json -Depth 4 -Compress
    [IO.File]::WriteAllText(
        $temporaryDockerConfigFile,
        $dockerConfigJson,
        [Text.UTF8Encoding]::new($false)
    )
    $env:DOCKER_CONFIG = $temporaryDockerConfig

    if ($LoginOnly) {
        Write-Host "Temporary ECR credential created successfully."
        return
    }

    Write-Host "Building Linux/amd64 image '$localImage'..."
    & docker build --platform linux/amd64 --tag $localImage --file $dockerfilePath $buildContext | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Docker image build failed."
    }

    Write-Host "Tagging and pushing '$imageUri'..."
    & docker tag $localImage $imageUri
    if ($LASTEXITCODE -ne 0) {
        throw "Could not tag the local image for ECR."
    }
    & docker push $imageUri | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Docker could not push the image to ECR. The ECR tag is immutable; use a new tag for the next attempt."
    }

    Write-Host "Image pushed successfully."
    Write-Output $imageUri
}
finally {
    $env:DOCKER_CONFIG = $previousDockerConfig
    $authorizationToken = $null
    $decodedAuthorization = $null
    $ecrPassword = $null
    $basicCredential = $null
    if (Test-Path -LiteralPath $temporaryDockerConfig -PathType Container) {
        $resolvedTemporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd(
            [IO.Path]::DirectorySeparatorChar,
            [IO.Path]::AltDirectorySeparatorChar
        )
        $resolvedDockerConfig = [IO.Path]::GetFullPath($temporaryDockerConfig)
        $expectedPrefix = $resolvedTemporaryRoot + [IO.Path]::DirectorySeparatorChar + "file-drive-docker-config-"
        if (-not $resolvedDockerConfig.StartsWith($expectedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to remove unexpected Docker configuration path '$resolvedDockerConfig'."
        }
        Remove-Item -LiteralPath $resolvedDockerConfig -Recurse -Force -Confirm:$false
    }
}
