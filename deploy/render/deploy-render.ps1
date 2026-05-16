# Render Deployment Helper - PowerShell Edition
# This script helps configure and manage Render deployments from Windows

param(
    [Parameter(Position = 0)]
    [ValidateSet("setup", "configure-secrets", "validate", "list-services", "instructions", "help")]
    [string]$Command = "help"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$RenderYamlPath = Join-Path $ProjectRoot "render.yaml"

# Colors
function Write-Success {
    param([string]$Message)
    Write-Host "✓ $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "⚠ $Message" -ForegroundColor Yellow
}

function Write-Error-Custom {
    param([string]$Message)
    Write-Host "✗ $Message" -ForegroundColor Red
}

function Write-Header {
    param([string]$Message)
    Write-Host ""
    Write-Host "=== $Message ===" -ForegroundColor Cyan
    Write-Host ""
}

function Test-RenderYaml {
    if (-not (Test-Path $RenderYamlPath)) {
        Write-Error-Custom "render.yaml not found at $RenderYamlPath"
        exit 1
    }
    
    $content = Get-Content $RenderYamlPath -Raw
    if ($content -notmatch "services:") {
        Write-Error-Custom "render.yaml appears malformed (missing 'services:')"
        exit 1
    }
    
    Write-Success "render.yaml is valid"
}

function Get-RenderServices {
    Write-Header "Services in render.yaml"
    
    $yaml = Get-Content $RenderYamlPath -Raw
    
    # Simple regex to extract service names
    $matchResults = [regex]::Matches($yaml, "name:\s*(\S+)")
    
    if ($matchResults.Count -gt 0) {
        foreach ($match in $matchResults) {
            $serviceName = $match.Groups[1].Value
            Write-Host "  • $serviceName"
        }
    }
}

function Set-RenderSecrets {
    Write-Header "Render Configuration Helper"
    
    Write-Host "This script will help you set up environment variables for Render."
    Write-Host ""
    
    $secrets = @{}
    
    # JWT Secret
    Write-Host "1. JWT_SECRET" -ForegroundColor Yellow
    Write-Host "   Used for signing JWT tokens. Generate a strong random value." -ForegroundColor Gray
    $jwtSecret = Read-Host "   Enter JWT_SECRET (or press Enter to generate)"
    if ([string]::IsNullOrWhiteSpace($jwtSecret)) {
        $jwtSecret = [System.Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
        Write-Success "Generated: $($jwtSecret.Substring(0, 20))..."
    }
    $secrets["JWT_SECRET"] = $jwtSecret
    
    Write-Host ""
    
    # S3 Configuration
    Write-Host "2. S3 Configuration (optional, for resume storage)" -ForegroundColor Yellow
    $useS3 = Read-Host "   Do you want to use S3? (y/n)"
    
    if ($useS3 -eq "y") {
        $s3Bucket = Read-Host "   S3 Bucket name"
        $secrets["STORAGE_S3_BUCKET"] = $s3Bucket
        
        $awsAccessKey = Read-Host "   AWS Access Key ID"
        $secrets["AWS_ACCESS_KEY_ID"] = $awsAccessKey
        
        Write-Host "   AWS Secret Access Key (will not be echoed)" -ForegroundColor Gray
        $awsSecret = Read-Host -AsSecureString "   Enter AWS Secret Access Key"
        $secrets["AWS_SECRET_ACCESS_KEY"] = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToCoTaskMemUnicode($awsSecret))
        
        $secrets["AWS_REGION"] = Read-Host "   AWS Region (default: us-east-1)"
        if ([string]::IsNullOrWhiteSpace($secrets["AWS_REGION"])) {
            $secrets["AWS_REGION"] = "us-east-1"
        }
    }
    
    Write-Host ""
    
    # SendGrid Configuration
    Write-Host "3. SendGrid Configuration (for email notifications)" -ForegroundColor Yellow
    $useSendGrid = Read-Host "   Do you want to use SendGrid? (y/n)"
    
    if ($useSendGrid -eq "y") {
        $sendGridKey = Read-Host "   SendGrid API Key"
        $secrets["SMTP_PASSWORD"] = $sendGridKey
        
        $smtpFrom = Read-Host "   SMTP From email address"
        $secrets["SMTP_FROM"] = $smtpFrom
    }
    
    # Summary
    Write-Host ""
    Write-Header "Configuration Summary"
    
    Write-Host "Environment variables to set in Render Dashboard:"
    Write-Host ""
    
    foreach ($key in $secrets.Keys) {
        $value = $secrets[$key]
        if ($value.Length -gt 50) {
            Write-Host "  $key = $($value.Substring(0, 47))..."
        } else {
            Write-Host "  $key = $value"
        }
    }
    
    Write-Host ""
    Write-Host "Next Steps:" -ForegroundColor Cyan
    Write-Host "1. Go to https://dashboard.render.com"
    Write-Host "2. Open your Blueprint"
    Write-Host "3. Go to 'Environment' tab"
    Write-Host "4. Add these environment variables"
    Write-Host "5. Click 'Deploy'"
    Write-Host ""
}

function Show-Instructions {
    Write-Header "Render Deployment Instructions"
    
    Write-Host "Method 1: One-Click Blueprint (Recommended)" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "1. Push your code to GitHub"
    Write-Host "2. Go to https://dashboard.render.com"
    Write-Host "3. Click 'New +' → 'Blueprint'"
    Write-Host "4. Connect your GitHub account and select this repository"
    Write-Host "5. Render auto-detects render.yaml"
    Write-Host "6. Configure environment variables:"
    Write-Host "   • JWT_SECRET (required)"
    Write-Host "   • AWS credentials (if using S3)"
    Write-Host "   • SendGrid key (if using email)"
    Write-Host "7. Click 'Create Blueprint'"
    Write-Host ""
    
    Write-Host "Method 2: Manual Service Setup" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "1. Create PostgreSQL database:"
    Write-Host "   • Dashboard → New Database → PostgreSQL"
    Write-Host "   • Name: talent_platform"
    Write-Host ""
    Write-Host "2. For each service, create Web Service:"
    Write-Host "   • Dashboard → New Web Service"
    Write-Host "   • Connect GitHub repo"
    Write-Host "   • Runtime: Docker"
    Write-Host "   • Set environment variables"
    Write-Host ""
    Write-Host "3. Update service URLs in each service config"
    Write-Host ""
    
    Write-Host "Expected Costs:" -ForegroundColor Cyan
    Write-Host "  • Backend services (5): ~$35-40/month"
    Write-Host "  • PostgreSQL: ~$30/month"
    Write-Host "  • Frontend (static): Free"
    Write-Host "  • Total: ~$70-75/month"
    Write-Host ""
}

function Show-Help {
    Write-Host ""
    Write-Host "Render Deployment Helper - PowerShell Edition" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Usage: ..\deploy-render.ps1 [command]" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Commands:" -ForegroundColor Yellow
    Write-Host "  setup                 Run complete setup wizard"
    Write-Host "  configure-secrets     Configure environment variables"
    Write-Host "  validate              Validate render.yaml"
    Write-Host "  list-services         List services in render.yaml (uses Get-RenderServices)"
    Write-Host "  instructions          Show deployment instructions"
    Write-Host "  help                  Show this help message"
    Write-Host ""
    Write-Host "Examples:" -ForegroundColor Gray
    Write-Host "  .\deploy-render.ps1 setup"
    Write-Host "  .\deploy-render.ps1 validate"
    Write-Host "  .\deploy-render.ps1 configure-secrets"
    Write-Host ""
}

# Main dispatch
switch ($Command) {
    "validate" {
        Test-RenderYaml
    }
    "list-services" {
        Get-RenderServices
    }
    "configure-secrets" {
        Set-RenderSecrets
    }
    "instructions" {
        Show-Instructions
    }
    "setup" {
        Write-Header "Render Setup Wizard"
        Test-RenderYaml
        Get-RenderServices
        $proceed = Read-Host "`nProceed with configuration? (y/n)"
        if ($proceed -eq "y") {
            Set-RenderSecrets
        }
    }
    "help" {
        Show-Help
    }
    default {
        Write-Error-Custom "Unknown command: $Command"
        Show-Help
        exit 1
    }
}
