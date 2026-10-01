[CmdletBinding()]
param(
    [string]$AwsProfile = "AdministratorAccess-678113404929",
    [string]$Region = "eu-central-1",
    [string]$AccountId = "678113404929",
    [string]$WebRepository = "file-drive-preview-web",
    [string]$ApiRepository = "file-drive-preview-api",
    [string]$ApiImageUri,
    [ValidateRange(0, 10)]
    [int]$ApiDesiredCount = 1,
    [ValidateRange(0, 10)]
    [int]$WebDesiredCount = 1,
    [string]$PlanFile = "web-reload.tfplan",
    [switch]$PlanOnly
)

$ErrorActionPreference = "Stop"

$pushImageScript = Join-Path $PSScriptRoot "push-preview-image.ps1"
$deployScript = Join-Path $PSScriptRoot "deploy-preview-foundation.ps1"

foreach ($scriptPath in @($pushImageScript, $deployScript)) {
    if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
        throw "Required helper script was not found at '$scriptPath'."
    }
}

Write-Host "Building and pushing a new frontend image..."
$webImageUri = ((& $pushImageScript `
    -Component web `
    -Profile $AwsProfile `
    -Region $Region `
    -AccountId $AccountId `
    -Repository $WebRepository) -join "").Trim()

if (-not $webImageUri) {
    throw "The frontend image was pushed without returning an image URI."
}

Write-Host "Frontend image ready: $webImageUri"
Write-Host "Creating a Terraform plan for the ECS web service..."

$deployArguments = @{
    AwsProfile      = $AwsProfile
    Region          = $Region
    AccountId       = $AccountId
    Repository      = $ApiRepository
    WebRepository   = $WebRepository
    WebImageUri     = $webImageUri
    DesiredCount    = $ApiDesiredCount
    WebDesiredCount = $WebDesiredCount
    PlanFile        = $PlanFile
    DeployWeb       = $true
}

if ($ApiImageUri) {
    $deployArguments.ImageUri = $ApiImageUri
}
if ($PlanOnly) {
    $deployArguments.PlanOnly = $true
}

& $deployScript @deployArguments

if ($PlanOnly) {
    Write-Host "Frontend image pushed and deployment plan created. No changes were applied."
}
else {
    $clusterName = "file-drive-preview"
    $serviceName = "file-drive-preview-web"

    Write-Host "Waiting for the ECS web service to become stable..."
    & aws ecs wait services-stable `
        --cluster $clusterName `
        --services $serviceName `
        --region $Region `
        --profile $AwsProfile
    if ($LASTEXITCODE -ne 0) {
        throw "The ECS web service did not become stable. Check its deployment events and task logs."
    }

    $activeTaskDefinition = ((& aws ecs describe-services `
        --cluster $clusterName `
        --services $serviceName `
        --region $Region `
        --profile $AwsProfile `
        --query "services[0].taskDefinition" `
        --output text) -join "").Trim()
    if ($LASTEXITCODE -ne 0 -or -not $activeTaskDefinition -or $activeTaskDefinition -eq "None") {
        throw "Could not determine the active ECS web task definition."
    }

    $activeWebImage = ((& aws ecs describe-task-definition `
        --task-definition $activeTaskDefinition `
        --region $Region `
        --profile $AwsProfile `
        --query "taskDefinition.containerDefinitions[?name=='web'].image | [0]" `
        --output text) -join "").Trim()
    if ($LASTEXITCODE -ne 0 -or -not $activeWebImage -or $activeWebImage -eq "None") {
        throw "Could not determine the image used by the active ECS web task definition."
    }
    if ($activeWebImage -ne $webImageUri) {
        throw "ECS is stable but is using '$activeWebImage' instead of the expected '$webImageUri'. The Terraform apply may have been cancelled."
    }

    Write-Host "Frontend deployment verified: $activeWebImage"
    Write-Host "Refresh the browser with Ctrl+Shift+R."
}

Write-Output $webImageUri
