$ErrorActionPreference = "Stop"

if (Test-Path "C:\Program Files\Android\Android Studio\jbr") {
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
    $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
}

$env:ANDROID_HOME = "C:\Users\ThinkPad\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT = "C:\Users\ThinkPad\AppData\Local\Android\Sdk"

Write-Host "Building Merged Test DPC APK..." -ForegroundColor Cyan
Set-Location "$PSScriptRoot\testdpc_source"
& ".\gradlew.bat" assembleNormalDebug
Write-Host "`nBUILD SUCCESSFUL! APK created at: $PSScriptRoot\testdpc_source\app\build\outputs\apk\normal\debug\TestDPC-normal-debug.apk" -ForegroundColor Green
