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

function Invoke-DockerRegistryLogin {
    param(
        [Parameter(Mandatory = $true)]
        [string]$DockerPath,
        [Parameter(Mandatory = $true)]
        [string]$Registry,
        [Parameter(Mandatory = $true)]
        [string]$Username,
        [Parameter(Mandatory = $true)]
        [string]$Password
    )

    # Windows PowerShell can encode text piped to native programs as UTF-16,
    # which corrupts ECR's token. Write UTF-8 directly to Docker's stdin.
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $DockerPath
    $startInfo.Arguments = "login --username $Username --password-stdin $Registry"
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    if ($startInfo.PSObject.Properties.Name -contains "StandardInputEncoding") {
        $startInfo.StandardInputEncoding = New-Object System.Text.UTF8Encoding($false)
    }

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    [void]$process.Start()
    $process.StandardInput.WriteLine($Password)
    $process.StandardInput.Close()
    $standardOutput = $process.StandardOutput.ReadToEnd()
    $standardError = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    $exitCode = $process.ExitCode
    $process.Dispose()

    if ($standardOutput) {
        Write-Host $standardOutput.TrimEnd()
    }
    if ($standardError) {
        Write-Host $standardError.TrimEnd()
    }
    return $exitCode
}

function Save-DockerDesktopCredential {
    param(
        [Parameter(Mandatory = $true)]
        [string]$CredentialHelperPath,
        [Parameter(Mandatory = $true)]
        [string]$Registry,
        [Parameter(Mandatory = $true)]
        [string]$Username,
        [Parameter(Mandatory = $true)]
        [string]$Password
    )

    $credentialJson = @{
        ServerURL = $Registry
        Username  = $Username
        Secret    = $Password
    } | ConvertTo-Json -Compress
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $CredentialHelperPath
    $startInfo.Arguments = "store"
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    if ($startInfo.PSObject.Properties.Name -contains "StandardInputEncoding") {
        $startInfo.StandardInputEncoding = New-Object System.Text.UTF8Encoding($false)
    }

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    [void]$process.Start()
    $process.StandardInput.WriteLine($credentialJson)
    $process.StandardInput.Close()
    $standardOutput = $process.StandardOutput.ReadToEnd()
    $standardError = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    $exitCode = $process.ExitCode
    $process.Dispose()
    $credentialJson = $null

    if ($standardOutput) {
        Write-Host $standardOutput.TrimEnd()
    }
    if ($standardError) {
        Write-Host $standardError.TrimEnd()
    }
    return $exitCode
}

foreach ($commandName in @("aws", "docker")) {
    if (-not (Get-Command $commandName -ErrorAction SilentlyContinue)) {
        throw "Required command '$commandName' was not found in PATH."
    }
}
$dockerPath = (Get-Command docker -ErrorAction Stop).Source
$credentialHelperPath = (Get-Command docker-credential-desktop -ErrorAction Stop).Source

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

Write-Host "Logging Docker in to $registry..."
$dockerAuthenticated = $false
foreach ($loginAttempt in 1..3) {
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

    $dockerLoginExitCode = Invoke-DockerRegistryLogin `
        -DockerPath $dockerPath `
        -Registry $registry `
        -Username $ecrUsername `
        -Password $ecrPassword
    if ($dockerLoginExitCode -eq 0) {
        $dockerAuthenticated = $true
    }
    elseif ($loginAttempt -eq 3) {
        Write-Warning "Docker's registry preflight failed; storing the ECR credential through Docker Desktop's credential helper."
        $credentialStoreExitCode = Save-DockerDesktopCredential `
            -CredentialHelperPath $credentialHelperPath `
            -Registry $registry `
            -Username $ecrUsername `
            -Password $ecrPassword
        if ($credentialStoreExitCode -eq 0) {
            $dockerAuthenticated = $true
        }
    }
    $authorizationToken = $null
    $decodedAuthorization = $null
    $ecrPassword = $null
    if ($dockerAuthenticated) {
        break
    }
    if ($loginAttempt -lt 3) {
        Write-Warning "ECR login attempt $loginAttempt failed; retrying with a fresh token."
        Start-Sleep -Seconds 2
    }
}
if (-not $dockerAuthenticated) {
    throw "Docker could not authenticate to ECR."
}
if ($LoginOnly) {
    Write-Host "Docker authentication test succeeded."
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
