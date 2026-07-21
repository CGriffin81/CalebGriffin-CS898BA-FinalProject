<#
.SYNOPSIS
    Builds the MTG Scanner debug APK using the project Gradle wrapper.

.DESCRIPTION
    Stops old daemons, sets JAVA_HOME to Android Studio JBR, runs clean + assembleDebug,
    and verifies the APK exists. Optionally installs to a connected device and launches.

.PARAMETER ProjectRoot
    Path to the project root. Defaults to the script's own directory.

.PARAMETER JavaHome
    Path to JDK/JBR. Defaults to Android Studio's bundled JBR.

.PARAMETER Install
    If specified, installs the APK to a connected device via adb.

.PARAMETER Launch
    If specified, launches the app after install.

.EXAMPLE
    .\Build-Apk.ps1
    .\Build-Apk.ps1 -Install -Launch
#>
[CmdletBinding()]
param(
    [string]$ProjectRoot = "",
    [string]$JavaHome = "C:\Program Files\Android\Android Studio\jbr",
    [switch]$Clean = $true,
    [switch]$Install,
    [switch]$Launch
)

$ErrorActionPreference = "Stop"

# Resolve project root
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Split-Path -Parent $PSCommandPath
}

$ApplicationId = "com.mtgscanner"
$MainActivity = ".MainActivity"
$ApkPath = "app\build\outputs\apk\debug\app-debug.apk"

function Write-Step {
    param([string]$Message)
    Write-Host "`n[build] $Message" -ForegroundColor Cyan
}

# Validate paths
if (-not (Test-Path -LiteralPath $ProjectRoot)) {
    throw "Project root not found: $ProjectRoot"
}
Set-Location -LiteralPath $ProjectRoot

$wrapper = Join-Path $ProjectRoot "gradlew.bat"
if (-not (Test-Path -LiteralPath $wrapper)) {
    throw "Gradle wrapper not found: $wrapper"
}
if (-not (Test-Path -LiteralPath $JavaHome)) {
    throw "Java home not found: $JavaHome"
}

# Configure environment
$env:JAVA_HOME = $JavaHome
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

Write-Step "JAVA_HOME = $env:JAVA_HOME"
Write-Step "Stopping old Gradle daemons"
& $wrapper --stop 2>$null

if ($Clean) {
    Write-Step "Cleaning project"
    & $wrapper clean
    if ($LASTEXITCODE -ne 0) { throw "Clean failed" }
}

Write-Step "Running unit tests"
& $wrapper testDebugUnitTest
if ($LASTEXITCODE -ne 0) { throw "Unit tests failed" }

Write-Step "Building debug APK"
& $wrapper assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Build failed" }

$resolvedApk = Join-Path $ProjectRoot $ApkPath
if (-not (Test-Path -LiteralPath $resolvedApk)) {
    throw "APK not found after build: $resolvedApk"
}

Write-Step "APK ready:"
Get-Item -LiteralPath $resolvedApk | Select-Object FullName, @{N="SizeMB";E={[math]::Round($_.Length/1MB,1)}}, LastWriteTime | Format-List

if ($Install) {
    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
        throw "adb not found on PATH. Install Android SDK Platform Tools."
    }
    Write-Step "Installing APK to connected device"
    & adb install -r $resolvedApk
    if ($LASTEXITCODE -ne 0) { throw "Install failed" }
}

if ($Launch) {
    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
        throw "adb not found on PATH"
    }
    Write-Step "Launching $ApplicationId"
    & adb shell am start -n "$ApplicationId/$MainActivity"
}

Write-Host "`n[build] Done." -ForegroundColor Green
