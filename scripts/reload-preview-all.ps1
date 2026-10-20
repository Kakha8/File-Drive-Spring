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
    [string]$PlanFile = "application-reload.tfplan",
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

function Push-PreviewImage {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("api", "web")]
        [string]$Component,
        [Parameter(Mandatory = $true)]
        [string]$Repository
    )

    $uri = ((& $pushImageScript `
        -Component $Component `
        -Profile $AwsProfile `
        -Region $Region `
        -AccountId $AccountId `
        -Repository $Repository) -join "").Trim()
    if (-not $uri) {
        throw "The $Component image was pushed without returning an image URI."
    }
    return $uri
}

Write-Host "Building and pushing the backend image..."
$apiImageUri = Push-PreviewImage -Component api -Repository $ApiRepository
Write-Host "Backend image ready: $apiImageUri"

Write-Host "Building and pushing the frontend image..."
$webImageUri = Push-PreviewImage -Component web -Repository $WebRepository
Write-Host "Frontend image ready: $webImageUri"

$deployArguments = @{
    AwsProfile      = $AwsProfile
    Region          = $Region
    AccountId       = $AccountId
    Repository      = $ApiRepository
    WebRepository   = $WebRepository
    ImageUri        = $apiImageUri
    WebImageUri     = $webImageUri
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
    Write-Host "Both images were pushed and the deployment plan was created. No changes were applied."
}
else {
    $clusterName = "file-drive-preview"
    $services = @("file-drive-preview-api", "file-drive-preview-web")

    Write-Host "Waiting for both ECS services to become stable..."
    & aws ecs wait services-stable --cluster $clusterName --services $services `
        --region $Region --profile $AwsProfile
    if ($LASTEXITCODE -ne 0) {
        throw "The ECS services did not become stable. Check their deployment events and task logs."
    }

    $expectedImages = @{
        "file-drive-preview-api" = @{ Container = "api"; Image = $apiImageUri }
        "file-drive-preview-web" = @{ Container = "web"; Image = $webImageUri }
    }

    foreach ($serviceName in $services) {
        $activeTaskDefinition = ((& aws ecs describe-services `
            --cluster $clusterName --services $serviceName `
            --region $Region --profile $AwsProfile `
            --query "services[0].taskDefinition" --output text) -join "").Trim()
        if ($LASTEXITCODE -ne 0 -or -not $activeTaskDefinition -or $activeTaskDefinition -eq "None") {
            throw "Could not determine the active task definition for '$serviceName'."
        }

        $containerName = $expectedImages[$serviceName].Container
        $expectedImage = $expectedImages[$serviceName].Image
        $activeImage = ((& aws ecs describe-task-definition `
            --task-definition $activeTaskDefinition `
            --region $Region --profile $AwsProfile `
            --query "taskDefinition.containerDefinitions[?name=='$containerName'].image | [0]" `
            --output text) -join "").Trim()
        if ($LASTEXITCODE -ne 0 -or -not $activeImage -or $activeImage -eq "None") {
            throw "Could not determine the active image for '$serviceName'."
        }
        if ($activeImage -ne $expectedImage) {
            throw "'$serviceName' is stable but is using '$activeImage' instead of '$expectedImage'. The Terraform apply may have been cancelled."
        }

        Write-Host "Verified $serviceName`: $activeImage"
    }

    Write-Host "Backend and frontend deployments verified. Refresh the browser with Ctrl+Shift+R."
}

[PSCustomObject]@{
    ApiImageUri = $apiImageUri
    WebImageUri = $webImageUri
}
