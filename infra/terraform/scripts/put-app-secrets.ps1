<#
.SYNOPSIS
    Tikkle 운영 비밀값 중 사람이 가진 옛 값 7개를 SSM Parameter Store에 넣는다.

.DESCRIPTION
    - 입력은 화면에 보이지 않고(Read-Host -AsSecureString) PowerShell 명령 이력에도 남지 않는다.
    - 화면에는 파라미터 이름, 글자 수, 등급, 버전만 나온다. 값은 출력하지 않는다.
    - 이미 있는 파라미터는 건너뛴다. 값을 바꿀 때만 -Overwrite 를 붙인다.
    - FCM 서비스 계정은 JSON 파일 경로를 주면 base64로 바꿔 넣는다(-FcmJsonPath).
      경로를 주지 않으면 base64 문자열을 붙여 넣는다. 어느 쪽이든 service_account JSON인지 확인한다.
    - 값이 4096바이트를 넘으면 Advanced 등급(유료, 개당 월 $0.05)으로 넣고 알린다.

    다음은 이 스크립트가 다루지 않는다(Terraform이 만든다, infra/terraform/live):
      JWT_SECRET, TIKKLE_ENCRYPT_SECRET_KEY (ssm_app.tf), DB_URL, DB_USERNAME, DB_PASSWORD (ssm_db.tf)

.PARAMETER Only
    일부만 넣을 때 이름을 준다. 예: -Only GEMINI_API_KEY,DEEPSEEK_API_KEY

.PARAMETER Overwrite
    이미 있는 파라미터의 값을 바꾼다(새 버전이 생긴다).

.PARAMETER FcmJsonPath
    FCM 서비스 계정 JSON 파일 경로.

.EXAMPLE
    $env:AWS_PROFILE = "tikkle"
    powershell -ExecutionPolicy Bypass -File .\infra\terraform\scripts\put-app-secrets.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\infra\terraform\scripts\put-app-secrets.ps1 -Only GEMINI_API_KEY -Overwrite
#>
[CmdletBinding()]
param(
    [string[]] $Only,
    [switch] $Overwrite,
    [string] $FcmJsonPath,
    [string] $AwsProfile = $env:AWS_PROFILE,
    [string] $Region = "ap-northeast-2",
    [string] $PathPrefix = "/tikkle/prod"
)

$ErrorActionPreference = "Stop"

$AllNames = @(
    "PAYMENT_SECRET_KEY",
    "GEMINI_API_KEY",
    "DEEPSEEK_API_KEY",
    "COOLSMS_API_KEY",
    "COOLSMS_API_SECRET",
    "COOLSMS_SENDER_NUMBER",
    "FCM_CREDENTIALS_BASE64"
)

if ([string]::IsNullOrWhiteSpace($AwsProfile)) {
    $AwsProfile = "tikkle"
}

$Names = $AllNames
if ($Only) {
    $Requested = @($Only | ForEach-Object { $_ -split "," } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    $Unknown = @($Requested | Where-Object { $AllNames -notcontains $_ })
    if ($Unknown.Count -gt 0) {
        throw "이 스크립트가 다루지 않는 이름: $($Unknown -join ', ')"
    }
    $Names = $Requested
}

function Invoke-Aws {
    param([string[]] $Arguments)
    $output = & aws @Arguments "--profile" $AwsProfile "--region" $Region
    if ($LASTEXITCODE -ne 0) {
        throw "aws 명령이 실패했다 (종료 코드 $LASTEXITCODE)"
    }
    return $output
}

function Test-ParameterExists {
    param([string] $FullName)
    $found = Invoke-Aws @("ssm", "describe-parameters", "--parameter-filters", "Key=Name,Values=$FullName", "--query", "length(Parameters)", "--output", "text")
    return ([int]$found -gt 0)
}

function Read-SecretText {
    param([string] $Prompt)
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return ([Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)).Trim()
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

function Test-ServiceAccountBase64 {
    param([string] $Base64)
    try {
        $json = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Base64))
        $parsed = $json | ConvertFrom-Json
        return ($parsed.type -eq "service_account")
    }
    catch {
        return $false
    }
}

function Get-FcmValue {
    if (-not [string]::IsNullOrWhiteSpace($FcmJsonPath)) {
        if (-not (Test-Path -LiteralPath $FcmJsonPath)) {
            throw "FCM JSON 파일이 없다: $FcmJsonPath"
        }
        $bytes = [IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $FcmJsonPath).Path)
        return [Convert]::ToBase64String($bytes)
    }
    return (Read-SecretText "FCM_CREDENTIALS_BASE64 (base64 문자열을 붙여 넣고 Enter — 화면에 보이지 않는다)")
}

Write-Host "[put-app-secrets] 프로필 $AwsProfile, 리전 $Region, 경로 $PathPrefix"

$results = @()
foreach ($name in $Names) {
    $fullName = "$PathPrefix/$name"

    if ((Test-ParameterExists $fullName) -and -not $Overwrite) {
        Write-Host "[건너뜀] $fullName — 이미 있다 (바꾸려면 -Overwrite)"
        $results += [pscustomobject]@{ Name = $fullName; Result = "skipped" }
        continue
    }

    if ($name -eq "FCM_CREDENTIALS_BASE64") {
        $value = Get-FcmValue
        if (-not (Test-ServiceAccountBase64 $value)) {
            throw "$fullName 값이 service_account JSON의 base64가 아니다. 넣지 않았다."
        }
    }
    else {
        $value = Read-SecretText "$name (붙여 넣고 Enter — 화면에 보이지 않는다)"
    }

    if ([string]::IsNullOrEmpty($value)) {
        throw "$fullName 값이 비어 있다. 넣지 않았다."
    }

    $byteCount = [Text.Encoding]::UTF8.GetByteCount($value)
    $tier = "Standard"
    if ($byteCount -gt 4096) {
        $tier = "Advanced"
        Write-Warning "$fullName 값이 ${byteCount}바이트로 4096을 넘어 Advanced 등급으로 넣는다 (개당 월 `$0.05)."
    }

    $arguments = @(
        "ssm", "put-parameter",
        "--name", $fullName,
        "--type", "SecureString",
        "--tier", $tier,
        "--description", "Tikkle app secret (entered by script)",
        "--value=$value",
        "--query", "Version",
        "--output", "text"
    )
    if ($Overwrite) {
        $arguments += "--overwrite"
    }
    else {
        $arguments += @("--tags", "Key=Project,Value=tikkle", "Key=ManagedBy,Value=script")
    }

    $version = Invoke-Aws $arguments
    $value = $null

    Write-Host "[넣음] $fullName — $($byteCount)바이트, $tier, 버전 $version"
    $results += [pscustomobject]@{ Name = $fullName; Result = "put v$version" }
}

[GC]::Collect()
Write-Host ""
Write-Host "[put-app-secrets] 끝"
$results | Format-Table -AutoSize
