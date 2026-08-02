# Disable Incognito mode in Google Chrome and InPrivate mode in Microsoft Edge
# NOTE: Run PowerShell as Administrator to apply changes.

# 1. Google Chrome
If (-not (Test-Path "HKLM:\SOFTWARE\Policies\Google\Chrome")) {
    New-Item -Path "HKLM:\SOFTWARE\Policies\Google\Chrome" -Force | Out-Null
}
Set-ItemProperty -Path "HKLM:\SOFTWARE\Policies\Google\Chrome" -Name "IncognitoModeAvailability" -Value 1 -Type DWord

If (-not (Test-Path "HKCU:\SOFTWARE\Policies\Google\Chrome")) {
    New-Item -Path "HKCU:\SOFTWARE\Policies\Google\Chrome" -Force | Out-Null
}
Set-ItemProperty -Path "HKCU:\SOFTWARE\Policies\Google\Chrome" -Name "IncognitoModeAvailability" -Value 1 -Type DWord

# 2. Microsoft Edge
If (-not (Test-Path "HKLM:\SOFTWARE\Policies\Microsoft\Edge")) {
    New-Item -Path "HKLM:\SOFTWARE\Policies\Microsoft\Edge" -Force | Out-Null
}
Set-ItemProperty -Path "HKLM:\SOFTWARE\Policies\Microsoft\Edge" -Name "InPrivateModeAvailability" -Value 1 -Type DWord

If (-not (Test-Path "HKCU:\SOFTWARE\Policies\Microsoft\Edge")) {
    New-Item -Path "HKCU:\SOFTWARE\Policies\Microsoft\Edge" -Force | Out-Null
}
Set-ItemProperty -Path "HKCU:\SOFTWARE\Policies\Microsoft\Edge" -Name "InPrivateModeAvailability" -Value 1 -Type DWord

Write-Host "Successfully updated Registry policy settings." -ForegroundColor Green
Write-Host "Incognito Mode (Chrome) and InPrivate Mode (Edge) are now disabled." -ForegroundColor Green
Write-Host "Please restart Chrome and Edge for changes to take effect." -ForegroundColor Yellow
