[CmdletBinding()]
param(
    [string]$ProjectRoot = "",
    [string]$JavaHome = "C:\Program Files\Android\Android Studio\jbr",
    [switch]$Clean = $true,
    [int]$MinimumFreeSpaceGB = 8,
    [switch]$Install,
    [switch]$Launch,
    [string]$ApplicationId = "com.mtgscanner",
    [string]$MainActivity = ".MainActivity",
    [string]$ApkPath = "app\build\outputs\apk\debug\app-debug.apk"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Split-Path -Parent $PSCommandPath
}

function Write-Step {
    param([string]$Message)
    Write-Host "[build] $Message"
}

function Assert-FreeSpace {
    param(
        [string]$Path,
        [int]$MinimumFreeSpaceGB
    )

    $drive = Split-Path -Qualifier $Path
    $psDrive = Get-PSDrive -Name $drive.TrimEnd(':') -ErrorAction SilentlyContinue
    if (-not $psDrive) {
        return
    }

    $freeSpaceGb = [math]::Round(($psDrive.Free / 1GB), 2)
    if ($freeSpaceGb -lt $MinimumFreeSpaceGB) {
        throw "Not enough free space on $drive. Available: $freeSpaceGb GB. Required: at least $MinimumFreeSpaceGB GB. Free disk space, then rerun the script."
    }
}

if (-not (Test-Path -LiteralPath $ProjectRoot)) {
    throw "Project root not found: $ProjectRoot"
}

Set-Location -LiteralPath $ProjectRoot
Assert-FreeSpace -Path $ProjectRoot -MinimumFreeSpaceGB $MinimumFreeSpaceGB

$wrapper = Join-Path $ProjectRoot "gradlew.bat"
if (-not (Test-Path -LiteralPath $wrapper)) {
    throw "Gradle wrapper not found: $wrapper"
}

if (-not (Test-Path -LiteralPath $JavaHome)) {
    throw "Java home not found: $JavaHome"
}

$env:JAVA_HOME = $JavaHome
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

Write-Step "Using JAVA_HOME = $env:JAVA_HOME"
Write-Step "Stopping old Gradle daemons"
& $wrapper --stop

Write-Step "Verifying wrapper runtime"
& $wrapper -version

if ($Clean) {
    Write-Step "Cleaning project"
    & $wrapper clean
}

Write-Step "Building debug APK"
& $wrapper assembleDebug

$resolvedApk = Join-Path $ProjectRoot $ApkPath
if (-not (Test-Path -LiteralPath $resolvedApk)) {
    throw "APK not found after build: $resolvedApk"
}

Write-Step "APK ready: $resolvedApk"
Get-Item -LiteralPath $resolvedApk | Select-Object FullName, Length, LastWriteTime | Format-List

if ($Install) {
    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
        throw "adb not found on PATH"
    }

    Write-Step "Installing APK to connected device"
    & adb install -r $resolvedApk
}

if ($Launch) {
    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
        throw "adb not found on PATH"
    }

    Write-Step "Launching app"
    & adb shell am start -n "$ApplicationId/$MainActivity"
}
