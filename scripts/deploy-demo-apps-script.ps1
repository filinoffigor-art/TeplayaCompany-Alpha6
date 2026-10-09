param(
  [switch]$SkipRuntimeVerify
)

$ErrorActionPreference = "Stop"

$ScriptId = "1dX85EHEZRii7tWK_wU_FBJxmIsW2PFqU15mAYy12UMjqngv3wvw5y2MR"
$DeploymentId = "AKfycbwoAeJf7fPZDGvteBsjrver2RhPGfooZdFZn-FhrZv_rnvxw-5FpvCcr6kfKFFeOdmv"
$ExpectedSpreadsheetId = "1msnOiHA2W_M2OI6eJLDFcL_mP1L_LWIirqsVZIa3IUQ"
$ExpectedDatabaseName = "Тёплая Компания — Управление и учёт ДЛЯ ДЕМО"
$ExpectedApi = "tk4-v5-demo-only"
$WebAppUrl = "https://script.google.com/macros/s/$DeploymentId/exec"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$SourceFile = Join-Path $RepoRoot "backend\google_apps_script\OperationalApi.gs"
if (!(Test-Path $SourceFile)) { throw "OperationalApi.gs not found: $SourceFile" }

$sourceText = Get-Content -Raw -Encoding UTF8 $SourceFile
foreach ($needle in @($ExpectedSpreadsheetId, $ExpectedDatabaseName, $ExpectedApi)) {
  if (!$sourceText.Contains($needle)) { throw "Source validation failed. Missing: $needle" }
}

$work = Join-Path $env:TEMP ("tk4-appsscript-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $work | Out-Null

function Clasp {
  param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Args)
  & npx -y @google/clasp@latest @Args
  if ($LASTEXITCODE -ne 0) { throw "clasp failed: $($Args -join ' ')" }
}

try {
  Push-Location $work

  Write-Host "1/7 Google authorization check..."
  & npx -y @google/clasp@latest show-authorized-user *> $null
  if ($LASTEXITCODE -ne 0) {
    Write-Host "Google authorization is required once. A browser window will open."
    Clasp login
  }

  Write-Host "2/7 Clone EXISTING Apps Script project..."
  Clasp clone-script $ScriptId --rootDir "."

  $remoteApi = Join-Path $work "OperationalApi.gs"
  if (!(Test-Path $remoteApi)) {
    throw "STOP: Existing project does not contain OperationalApi.gs. Nothing was created or deployed."
  }

  Write-Host "3/7 Replace ONLY OperationalApi.gs..."
  Copy-Item -Force $SourceFile $remoteApi

  Write-Host "4/7 Push existing project back with only OperationalApi.gs changed..."
  Clasp push --force

  if (!$SkipRuntimeVerify) {
    Write-Host "5/7 Run verifyOperationalDatabase..."
    & npx -y @google/clasp@latest run-function verifyOperationalDatabase
    if ($LASTEXITCODE -ne 0) {
      throw "Runtime verification failed. Existing deployment was NOT updated. If clasp reports that API Executable is not published, run this script again with -SkipRuntimeVerify only after manually running verifyOperationalDatabase in Apps Script."
    }
  } else {
    Write-Host "5/7 Runtime verification skipped by explicit flag."
  }

  Write-Host "6/7 Confirm EXISTING deployment and redeploy the same deployment ID..."
  $deployments = & npx -y @google/clasp@latest list-deployments
  if ($LASTEXITCODE -ne 0) { throw "Could not list deployments." }
  $deploymentText = ($deployments | Out-String)
  if (!$deploymentText.Contains($DeploymentId)) {
    throw "STOP: Existing deployment ID was not found. No new deployment will be created."
  }
  Clasp create-deployment --deploymentId $DeploymentId --description "TK4 demo API $ExpectedApi"

  Write-Host "7/7 Check existing /exec URL..."
  $health = Invoke-RestMethod -Method Get -Uri $WebAppUrl
  if ($health.api -ne $ExpectedApi) { throw "Health mismatch: api=$($health.api)" }
  if ($health.spreadsheetId -ne $ExpectedSpreadsheetId) { throw "Health mismatch: spreadsheetId=$($health.spreadsheetId)" }
  if ($health.databaseName -ne $ExpectedDatabaseName) { throw "Health mismatch: databaseName=$($health.databaseName)" }

  Write-Host ""
  Write-Host "SUCCESS"
  Write-Host "API: $($health.api)"
  Write-Host "Spreadsheet: $($health.spreadsheetId)"
  Write-Host "Database: $($health.databaseName)"
  Write-Host "Existing /exec preserved: $WebAppUrl"
}
finally {
  Pop-Location -ErrorAction SilentlyContinue
  Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
}
