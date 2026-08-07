# Requires Administrator Privilege
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host "Please run this PowerShell script as Administrator!" -ForegroundColor Red
    Exit
}

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  Enabling Windows Adult Content & Domain Protection" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

# 1. Apply CleanBrowsing Family DNS to all Active Network Adapters
Write-Host "`n1. Configuring Family-Filter DNS on Network Adapters..." -ForegroundColor Yellow
$adapters = Get-NetAdapter | Where-Object { $_.Status -eq "Up" }
foreach ($adapter in $adapters) {
    Set-DnsClientServerAddress -InterfaceAlias $adapter.Name -ServerAddresses ("185.228.168.168", "185.228.169.168")
    Write-Host "   [+] Set CleanBrowsing Family DNS on: $($adapter.Name)" -ForegroundColor Green
}

# 2. Update Hosts File for SafeSearch & Blocked Domains (Including Complete X/Twitter Block)
Write-Host "`n2. Updating System Hosts File for SafeSearch & Domain Block..." -ForegroundColor Yellow
$hostsPath = "$env:SystemRoot\System32\drivers\etc\hosts"
$hostsEntries = @(
    "216.239.38.120 www.google.com",
    "216.239.38.120 google.com",
    "216.239.38.120 forcesafesearch.google.com",
    "216.239.38.120 www.bing.com",
    "216.239.38.120 strict.bing.com",
    "0.0.0.0 www.fboxtv.org",
    "0.0.0.0 fboxtv.org",
    "0.0.0.0 x.com",
    "0.0.0.0 www.x.com",
    "0.0.0.0 api.x.com",
    "0.0.0.0 twitter.com",
    "0.0.0.0 www.twitter.com",
    "0.0.0.0 mobile.twitter.com",
    "0.0.0.0 api.twitter.com",
    "0.0.0.0 twimg.com",
    "0.0.0.0 www.twimg.com",
    "0.0.0.0 pbs.twimg.com",
    "0.0.0.0 video.twimg.com",
    "0.0.0.0 abs.twimg.com",
    "0.0.0.0 ton.twimg.com",
    "0.0.0.0 media.twimg.com"
)

$existingContent = [System.IO.File]::ReadAllText($hostsPath)
$newEntriesToAdd = @()

foreach ($entry in $hostsEntries) {
    if (-not $existingContent.Contains($entry)) {
        $newEntriesToAdd += $entry
        Write-Host "   [+] Adding hosts entry: $entry" -ForegroundColor Green
    }
}

if ($newEntriesToAdd.Count -gt 0) {
    $textToAppend = "`r`n" + ($newEntriesToAdd -join "`r`n")
    [System.IO.File]::AppendAllText($hostsPath, $textToAppend)
} else {
    Write-Host "   [+] All hosts entries are already present." -ForegroundColor Green
}

# 3. Apply Registry Policies (Chrome, Edge, DoH Disable, Total X/Twitter Block, VPN & Proxy Lock)
Write-Host "`n3. Applying Registry Policies (Browser Policies & fboxtv.org / X Total Block)..." -ForegroundColor Yellow
$regPath = "d:\Ai studio\DpcLocker + Windows incognito Blocker\enable_windows_protection.reg"
reg import "$regPath"
Write-Host "   [+] Applied Chrome, Edge & Windows Registry Policies" -ForegroundColor Green

# 4. Stop and Disable Windows RasMan Service (Built-in VPN)
Write-Host "`n4. Disabling Windows VPN Service (RasMan)..." -ForegroundColor Yellow
Stop-Service -Name "RasMan" -Force -ErrorAction SilentlyContinue
Set-Service -Name "RasMan" -StartupType Disabled -ErrorAction SilentlyContinue
Write-Host "   [+] Disabled Remote Access Connection Manager (Windows VPN)" -ForegroundColor Green

# 5. Flush DNS Cache and Reset Browser Sockets
Write-Host "`n5. Flushing DNS Cache & Resetting Browser Connections..." -ForegroundColor Yellow
Clear-DnsClientCache
Stop-Process -Name "chrome" -Force -ErrorAction SilentlyContinue
Stop-Process -Name "msedge" -Force -ErrorAction SilentlyContinue
Write-Host "   [+] DNS Cache flushed and browser processes reset successfully!" -ForegroundColor Green

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "  DOMAIN & ADULT PROTECTION IS NOW ACTIVE ON WINDOWS!" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
