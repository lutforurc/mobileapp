#Requires -Version 5.1
<#
    Prepares an ISOLATED review/development environment for the CashBookBD
    backend: its own checkout, its own .env, and a NEW EMPTY database.

    Read the guards before running. It never writes to, migrates, or selects from
    an existing business database:
      * the target database name must start with `cashbookbd_review` and must not
        be any known business database (refused by name);
      * the database must not already exist (the PHP helper refuses to touch it);
      * the source checkout is only ever READ (its .env is copied, never edited);
      * migrations run only after the review checkout's own connection has been
        asked what database it is actually on, and the answer checked.

    Secrets: the copied .env keeps the source's credentials. This script never
    prints, logs, or echoes a password.

    Usage:
        .\setup-review-env.ps1                 # do everything
        .\setup-review-env.ps1 -WhatIf         # show what it would do
#>
param(
    [string]$Source      = 'F:\All_Database\www\cashbook_api',
    [string]$ReviewRoot  = 'F:\All_Database\www\cashbook_review',
    [string]$ReviewDb    = 'cashbookbd_review',
    [string]$ReviewHost  = 'cashbook_review.test',
    [switch]$WhatIf
)

# Native calls write progress/warnings to stderr; with 'Stop' PowerShell would
# turn that into a terminating error and abort silently. Failures are handled
# explicitly through $LASTEXITCODE instead.
$ErrorActionPreference = 'Continue'

$ProtectedDbs = @(
    'krishibitandatabase', 'myappsaasdatabase', 'cashbookbd',
    'cashbook_api', 'information_schema', 'mysql', 'performance_schema', 'sys'
)

function Fail($msg) { Write-Host "`nSTOPPED: $msg" -ForegroundColor Red; exit 1 }
function Step($msg) { Write-Host "`n=== $msg" -ForegroundColor Cyan }

# dotenv rejects an unquoted value containing whitespace, so quote when needed.
function EnvValue($v) {
    if ($v -match '\s' -and $v -notmatch '^".*"$') { return '"' + $v + '"' }
    return $v
}

# Runs a native command, prints its output, and fails loudly on a non-zero exit.
function Run($exe, [string[]]$arguments, $label) {
    $output = & $exe @arguments 2>&1
    $code = $LASTEXITCODE
    if ($output) { $output | ForEach-Object { '  ' + $_ } }
    if ($code -ne 0) { Fail "$label failed (exit $code) - see the output above." }
    return $output
}

Step 'Guards'
if (-not $ReviewDb.StartsWith('cashbookbd_review')) {
    Fail "'$ReviewDb' does not start with 'cashbookbd_review'. This script only builds review databases."
}
if ($ProtectedDbs -contains $ReviewDb) { Fail "'$ReviewDb' is a protected business database name." }
if (-not (Test-Path (Join-Path $Source 'artisan'))) { Fail "'$Source' is not a Laravel app (no artisan)." }
if (-not (Test-Path (Join-Path $Source '.env'))) { Fail "'$Source' has no .env to copy configuration from." }

$sourceEnvDb = (Get-Content (Join-Path $Source '.env') |
    Where-Object { $_ -match '^DB_DATABASE=' } | Select-Object -First 1) -replace '^DB_DATABASE=', ''
if ($sourceEnvDb.Trim() -eq $ReviewDb) {
    Fail "the source .env already points at '$ReviewDb'; refusing to run against the source in place."
}
Write-Host "  source        : $Source"
Write-Host "  review root   : $ReviewRoot"
Write-Host "  review db     : $ReviewDb   (new, empty)"
Write-Host "  review host   : http://$ReviewHost"
Write-Host "  source db     : $sourceEnvDb  (read-only; never written to)"

$php = (Get-Command php -ErrorAction SilentlyContinue).Source
if (-not $php) {
    $candidate = Join-Path $env:USERPROFILE '.config\herd\bin\php84.bat'
    if (Test-Path $candidate) { $php = $candidate } else { Fail 'no php binary found on PATH or in the Herd bin directory.' }
}
Write-Host "  php           : $php"

if ($WhatIf) { Write-Host "`n-WhatIf set: stopping before any change." -ForegroundColor Yellow; exit 0 }

# ---------------------------------------------------------------- 1. checkout
Step 'Checkout'
if (Test-Path (Join-Path $ReviewRoot '.git')) {
    Write-Host '  already cloned - leaving it alone'
} else {
    if (Test-Path $ReviewRoot) { Fail "$ReviewRoot exists but is not a git checkout. Move it aside first." }
    Run 'git' @('clone', '--local', '--quiet', $Source, $ReviewRoot) 'git clone'
    Write-Host '  cloned'
}

# Dependencies are copied from the source checkout rather than fetched, so this
# needs no network and cannot drift from what the source is running.
$srcVendor = Join-Path $Source 'vendor'
$dstVendor = Join-Path $ReviewRoot 'vendor'
if (-not (Test-Path (Join-Path $dstVendor 'autoload.php'))) {
    if (-not (Test-Path $srcVendor)) { Fail 'no vendor/ in the source checkout; run composer install there first.' }
    Write-Host '  copying vendor/ (dependency code only - no application data)'
    Copy-Item $srcVendor $dstVendor -Recurse -Force
}
if (-not (Test-Path (Join-Path $dstVendor 'autoload.php'))) { Fail 'vendor/autoload.php is missing after the copy.' }

# git does not track empty directories, so the runtime directories a Laravel
# skeleton keeps in place with a .gitignore are missing from a fresh clone.
# Without bootstrap/cache in particular, artisan refuses to start.
Step 'Runtime directories'
$runtimeDirs = @(
    'bootstrap\cache',
    'storage\app\public',
    'storage\framework\cache\data',
    'storage\framework\sessions',
    'storage\framework\testing',
    'storage\framework\views',
    'storage\logs'
)
foreach ($d in $runtimeDirs) {
    $full = Join-Path $ReviewRoot $d
    if (-not (Test-Path $full)) { New-Item -ItemType Directory -Path $full -Force | Out-Null; Write-Host "  created $d" }
}

# ------------------------------------------------------------------- 2. .env
Step '.env for the review checkout'
$srcEnvText = [System.IO.File]::ReadAllText((Join-Path $Source '.env'))
$reviewEnv = $srcEnvText

# Override only what must differ. DB credentials are carried across untouched.
$overrides = [ordered]@{
    'APP_NAME'    = 'CashBookBD Review'
    'APP_ENV'     = 'local'
    'APP_DEBUG'   = 'true'
    'APP_URL'     = "http://$ReviewHost"
    'DB_DATABASE' = $ReviewDb
    'SMS_ENABLED' = 'false'   # a synthetic environment never sends a real SMS
}
foreach ($k in $overrides.Keys) {
    $line = "$k=$(EnvValue $overrides[$k])"
    if ($reviewEnv -match "(?m)^$k=.*$") { $reviewEnv = [regex]::Replace($reviewEnv, "(?m)^$k=.*$", $line) }
    else { $reviewEnv = $reviewEnv.TrimEnd() + "`n" + $line + "`n" }
}
$envPath = Join-Path $ReviewRoot '.env'
[System.IO.File]::WriteAllText($envPath, $reviewEnv, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "  written: $envPath   (credentials copied, not displayed)"
Write-Host "  DB_DATABASE=$ReviewDb   APP_URL=http://$ReviewHost   SMS_ENABLED=false"

# A cached config would ignore .env, so clear it in the review checkout only.
Run $php @((Join-Path $ReviewRoot 'artisan'), 'config:clear') 'artisan config:clear' | Out-Null

# --------------------------------------------------------- 3. empty database
Step "New empty database ($ReviewDb)"
$helper = Join-Path $ReviewRoot '_review_db.php'
Copy-Item (Join-Path $PSScriptRoot '_review_db.php') $helper -Force
Run $php @($helper, $ReviewDb) 'database creation' | Out-Null

# ------------------------------------------------------------- 4. migrations
Step 'Migrations'
# Ask the review connection what it is actually on before migrating. Mirrors the
# guard in the repo's own _site_patch_run.php.
$probe = Run $php @((Join-Path $ReviewRoot 'artisan'), 'tinker', '--execute=echo DB::connection()->getDatabaseName();') 'database probe'
$actual = "$($probe | Where-Object { $_ -and "$_".Trim() -ne '' } | Select-Object -Last 1)".Trim()
Write-Host "  connected database: '$actual'"
if ($actual -ne $ReviewDb) { Fail "the review checkout is connected to '$actual', not '$ReviewDb'. Refusing to migrate." }
if ($ProtectedDbs -contains $actual) { Fail "'$actual' is a protected business database. Refusing to migrate." }

Run $php @((Join-Path $ReviewRoot 'artisan'), 'migrate', '--force') 'artisan migrate' | Out-Null

Step 'Done'
Write-Host "  review app : $ReviewRoot   (Herd serves it at http://$ReviewHost)"
Write-Host "  database   : $ReviewDb  (synthetic data only)"
Write-Host "`nNext: .\seed-synthetic-company.ps1   to create the fictional company."
Write-Host "      tools\review-env\README.md      explains what each piece is for."
