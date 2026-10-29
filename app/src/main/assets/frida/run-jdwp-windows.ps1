#Requires -Version 5.1
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$targetPath = Join-Path $root "target.txt"
if (-not (Test-Path $targetPath)) {
    throw "target.txt is missing. Export a fresh kit from ELSEWHERE."
}
$target = (Get-Content -Raw $targetPath).Trim()
if ([string]::IsNullOrWhiteSpace($target)) {
    throw "target.txt is empty."
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb was not found in PATH. Install Android platform-tools and enable USB debugging."
}

$python = Get-Command python -ErrorAction SilentlyContinue
$pythonArgs = @()
if ($null -eq $python) {
    $python = Get-Command py -ErrorAction SilentlyContinue
    if ($null -ne $python) { $pythonArgs = @("-3") }
}
if ($null -eq $python) {
    throw "Python 3 was not found in PATH."
}

$loader = Join-Path $root "frida-jdwp-loader.py"
$script = Join-Path $root "elsewhere-sensor.js"
if (-not (Test-Path $loader)) { throw "frida-jdwp-loader.py is missing." }
if (-not (Test-Path $script)) { throw "elsewhere-sensor.js is missing." }

Write-Host "ELSEWHERE JDWP kit"
Write-Host "Target : $target"
Write-Host "Loader : $loader"
Write-Host "Script : $script"
Write-Host ""
Write-Host "Starting frida-jdwp-loader. The target will be restarted."
Write-Host "Logs: adb logcat -s ELSEWHERE-SENSOR:*"
Write-Host ""

$loaderArgs = @("frida", "-n", $target, "-i", "script", "-l", $script)
$localGadget = Get-ChildItem $root -Filter "frida-gadget-*.so" -File -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($null -ne $localGadget) {
    Write-Host "Using local Gadget: $($localGadget.FullName)"
    $loaderArgs += @("-g", $localGadget.FullName)
}

& $python.Source @pythonArgs $loader @loaderArgs
if ($LASTEXITCODE -ne 0) {
    throw "frida-jdwp-loader failed with exit code $LASTEXITCODE."
}
