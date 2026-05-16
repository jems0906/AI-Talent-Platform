param(
    [string]$TenantId = "acme",
    [string]$RecruiterEmail = "recruiter_smoke_acme@test.local",
    [Alias("RecruiterPassword")]
    [SecureString]$RecruiterSecret,
    [string]$ResumePath = "sample-resume.txt"
)

$ErrorActionPreference = "Stop"

function Step($message) {
    Write-Host "`n=== $message ===" -ForegroundColor Cyan
}

function Test-Condition($condition, $message) {
    if (-not $condition) {
        throw $message
    }
}

function ConvertTo-PlainText([SecureString]$secureValue) {
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

function Invoke-JsonPost($uri, $body, $headers) {
    return Invoke-RestMethod -Uri $uri -Method Post -ContentType "application/json" -Body ($body | ConvertTo-Json) -Headers $headers -TimeoutSec 60
}

try {
    Step "Preflight"
    Test-Condition (Test-Path $ResumePath) "Resume file not found: $ResumePath"

    if (-not $RecruiterSecret) {
        $RecruiterSecret = Read-Host "Recruiter password" -AsSecureString
    }

    $recruiterSecretPlain = ConvertTo-PlainText $RecruiterSecret

    $frontendStatus = (Invoke-WebRequest -Uri "http://localhost:5173" -UseBasicParsing -TimeoutSec 20).StatusCode
    $gatewayHealth = (Invoke-WebRequest -Uri "http://localhost:8080/health" -UseBasicParsing -TimeoutSec 20).Content
    Test-Condition ($frontendStatus -eq 200) "Frontend check failed"
    Test-Condition ($gatewayHealth -eq "ok") "Gateway health check failed"
    Write-Host "Frontend: $frontendStatus, Gateway: $gatewayHealth"

    Step "Register or login recruiter"
    $registerBody = @{
        tenantId = $TenantId
        name = "Smoke Recruiter"
        email = $RecruiterEmail
        password = $recruiterSecretPlain
        role = "RECRUITER"
    }

    $auth = $null
    try {
        $auth = Invoke-JsonPost -uri "http://localhost:8080/api/auth/register" -body $registerBody -headers @{}
    } catch {
        $loginBody = @{
            tenantId = $TenantId
            email = $RecruiterEmail
            password = $recruiterSecretPlain
        }
        $auth = Invoke-JsonPost -uri "http://localhost:8080/api/auth/login" -body $loginBody -headers @{}
    }

    Test-Condition ($null -ne $auth.token -and $auth.token.Length -gt 20) "Did not receive valid JWT token"
    $headers = @{
        "Authorization" = "Bearer $($auth.token)"
        "X-Tenant-Id" = $TenantId
    }
    Write-Host "Authenticated recruiter: $($auth.email)"

    Step "Apply candidate"
    $candidateEmail = "candidate_$(Get-Date -Format yyyyMMddHHmmss)@test.local"
    $applyRaw = & curl.exe -s -X POST "http://localhost:8080/api/candidates/apply" -F "tenantId=$TenantId" -F "name=Smoke Candidate" -F "email=$candidateEmail" -F "jobId=job-001" -F "resume=@$ResumePath"
    $candidate = $applyRaw | ConvertFrom-Json
    Test-Condition ($null -ne $candidate.id) "Candidate apply failed: $applyRaw"
    Write-Host "Candidate applied: $($candidate.id)"

    Step "List, rank, and invite"
    $list = Invoke-RestMethod -Uri "http://localhost:8080/api/candidates" -Method Get -Headers $headers -TimeoutSec 60
    Test-Condition ($list.Count -ge 1) "Expected at least one candidate"

    $rank = Invoke-JsonPost -uri "http://localhost:8080/api/candidates/$($candidate.id)/rank" -body @{ jobDescription = "Java Spring Boot microservices REST Docker" } -headers $headers
    Test-Condition ($null -ne $rank.score) "Ranking did not return a score"

    $invite = Invoke-JsonPost -uri "http://localhost:8080/api/candidates/$($candidate.id)/invite" -body @{ subject = "Interview Invitation"; message = "You are invited for an interview." } -headers $headers
    Test-Condition ($invite.candidate.status -eq "INTERVIEW_INVITED") "Invite did not update candidate status"

    Step "Success"
    Write-Host "SMOKE TEST PASSED" -ForegroundColor Green
    Write-Host "CandidateId=$($candidate.id) Score=$($rank.score) Status=$($invite.candidate.status)"
    exit 0
}
catch {
    Write-Host "SMOKE TEST FAILED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
