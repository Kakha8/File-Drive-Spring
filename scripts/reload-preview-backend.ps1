[CmdletBinding()]
param(
    [string]$AwsProfile = "AdministratorAccess-678113404929",
    [string]$Region = "eu-central-1",
    [string]$AccountId = "678113404929",
    [string]$ApiRepository = "file-drive-preview-api",
    [string]$WebRepository = "file-drive-preview-web",
    [ValidateRange(0, 10)]
    [int]$ApiDesiredCount = 1,
    [ValidateRange(0, 10)]
    [int]$WebDesiredCount = 1,
    [string]$PlanFile = "api-reload.tfplan",
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

Write-Host "Building and pushing a new backend image..."
$apiImageUri = ((& $pushImageScript `
    -Component api `
    -Profile $AwsProfile `
    -Region $Region `
    -AccountId $AccountId `
    -Repository $ApiRepository) -join "").Trim()

if (-not $apiImageUri) {
    throw "The backend image was pushed without returning an image URI."
}

Write-Host "Backend image ready: $apiImageUri"
Write-Host "Creating a Terraform plan for the ECS API service..."

# DeployWeb keeps the existing web service at one task. When WebImageUri is
# omitted, the deployment helper resolves the newest existing web image.
$deployArguments = @{
    AwsProfile      = $AwsProfile
    Region          = $Region
    AccountId       = $AccountId
    Repository      = $ApiRepository
    WebRepository   = $WebRepository
    ImageUri        = $apiImageUri
    DesiredCount    = $ApiDesiredCount
    WebDesiredCount = $WebDesiredCount
    PlanFile        = $PlanFile
    DeployWeb       = $true
}

if ($PlanOnly) {
    $deployArguments.PlanOnly = $true
}

& $deployScript @deployArguments

if ($PlanOnly) {
    Write-Host "Backend image pushed and deployment plan created. No changes were applied."
}
else {
    $clusterName = "file-drive-preview"
    $serviceName = "file-drive-preview-api"

    Write-Host "Waiting for the ECS API service to become stable..."
    & aws ecs wait services-stable --cluster $clusterName --services $serviceName `
        --region $Region --profile $AwsProfile
    if ($LASTEXITCODE -ne 0) {
        throw "The ECS API service did not become stable. Check its deployment events and task logs."
    }

    $activeTaskDefinition = ((& aws ecs describe-services `
        --cluster $clusterName --services $serviceName `
        --region $Region --profile $AwsProfile `
        --query "services[0].taskDefinition" --output text) -join "").Trim()
    if ($LASTEXITCODE -ne 0 -or -not $activeTaskDefinition -or $activeTaskDefinition -eq "None") {
        throw "Could not determine the active ECS API task definition."
    }

    $activeApiImage = ((& aws ecs describe-task-definition `
        --task-definition $activeTaskDefinition `
        --region $Region --profile $AwsProfile `
        --query "taskDefinition.containerDefinitions[?name=='api'].image | [0]" `
        --output text) -join "").Trim()
    if ($LASTEXITCODE -ne 0 -or -not $activeApiImage -or $activeApiImage -eq "None") {
        throw "Could not determine the image used by the active ECS API task definition."
    }
    if ($activeApiImage -ne $apiImageUri) {
        throw "ECS is stable but is using '$activeApiImage' instead of '$apiImageUri'. The Terraform apply may have been cancelled."
    }

    Write-Host "Backend deployment verified: $activeApiImage"
}

Write-Output $apiImageUri
