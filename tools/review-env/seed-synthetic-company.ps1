#Requires -Version 5.1
<#
    Creates the fictional company in the isolated review database by running
    _review_seed.php inside the review checkout.

    The seed itself drives the product's own registration provisioning
    (POST /api/register/verify-otp through the real HTTP kernel), so no tenant
    schema is hand-written. Everything it inserts is invented.

    Usage:
        .\seed-synthetic-company.ps1
        .\seed-synthetic-company.ps1 -ReviewRoot 'F:\All_Database\www\cashbook_review'
#>
param(
    [string]$ReviewRoot = 'F:\All_Database\www\cashbook_review'
)

$ErrorActionPreference = 'Continue'

if (-not (Test-Path (Join-Path $ReviewRoot 'artisan'))) {
    Write-Host "STOPPED: '$ReviewRoot' is not a review checkout. Run setup-review-env.ps1 first." -ForegroundColor Red
    exit 1
}

$php = (Get-Command php -ErrorAction SilentlyContinue).Source
if (-not $php) {
    $candidate = Join-Path $env:USERPROFILE '.config\herd\bin\php84.bat'
    if (Test-Path $candidate) { $php = $candidate } else { Write-Host 'STOPPED: no php binary found.' -ForegroundColor Red; exit 1 }
}

Copy-Item (Join-Path $PSScriptRoot '_review_seed.php') (Join-Path $ReviewRoot '_review_seed.php') -Force

Write-Host 'Seeding a fictional company (this refuses to run outside a cashbookbd_review* database)'
& $php (Join-Path $ReviewRoot '_review_seed.php') 2>&1 | ForEach-Object { '  ' + ($_ -replace '\x1b\[[0-9;]*m', '') }
$code = $LASTEXITCODE

if ($code -ne 0) {
    Write-Host "`nSeeding failed (exit $code). If the message mentions a missing table or column, the schema did not" -ForegroundColor Red
    Write-Host 'build - see the blocker section in tools\review-env\README.md.' -ForegroundColor Red
}
exit $code
