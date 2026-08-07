# Requires Administrator Privilege
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host "Please run this PowerShell script as Administrator!" -ForegroundColor Red
    Exit
}

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  Enabling Windows Adult Content & Domain Protection" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

# 1. Apply Cloudflare Family DNS (1.1.1.3 / 1.0.0.3) to all Active Network Adapters
Write-Host "`n1. Configuring Cloudflare Family-Filter DNS on Network Adapters..." -ForegroundColor Yellow
$adapters = Get-NetAdapter | Where-Object { $_.Status -eq "Up" }
foreach ($adapter in $adapters) {
    Set-DnsClientServerAddress -InterfaceAlias $adapter.Name -ServerAddresses ("1.1.1.3", "1.0.0.3")
    Write-Host "   [+] Set Cloudflare Family DNS (1.1.1.3 / 1.0.0.3) on: $($adapter.Name)" -ForegroundColor Green
}

# 2. Clean Up Old Discord Entries from Hosts File & Purge Stale URLBlocklist / Extension Registry Keys
Write-Host "`n2. Cleaning Up Allowed Domains & Purging Stale Registry Keys..." -ForegroundColor Yellow
$hostsPath = "$env:SystemRoot\System32\drivers\etc\hosts"
if (Test-Path $hostsPath) {
    $lines = Get-Content $hostsPath | Where-Object { $_ -notmatch "discord" -and $_ -notmatch "chromewebstore" }
    [System.IO.File]::WriteAllLines($hostsPath, $lines)
    Write-Host "   [+] Removed Discord & WebStore entries from hosts file" -ForegroundColor Green
}

Remove-Item -Path "HKLM:\SOFTWARE\Policies\Google\Chrome\URLBlocklist" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "HKLM:\SOFTWARE\Policies\Google\Chrome\ExtensionInstallBlocklist" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "HKLM:\SOFTWARE\Policies\Microsoft\Edge\URLBlocklist" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "HKLM:\SOFTWARE\Policies\Microsoft\Edge\ExtensionInstallBlocklist" -Recurse -Force -ErrorAction SilentlyContinue
Write-Host "   [+] Purged old registry extension & blocklist restriction keys" -ForegroundColor Green

# 3. Update Hosts File for SafeSearch & Blocked Domains (X, Reddit, Tumblr, Telegram, Proxies - Discord & Web Store Allowed)
Write-Host "`n3. Updating System Hosts File for SafeSearch & Notorious Domains..." -ForegroundColor Yellow
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
    "0.0.0.0 media.twimg.com",
    "0.0.0.0 reddit.com",
    "0.0.0.0 www.reddit.com",
    "0.0.0.0 old.reddit.com",
    "0.0.0.0 i.redd.it",
    "0.0.0.0 v.redd.it",
    "0.0.0.0 preview.redd.it",
    "0.0.0.0 redditmedia.com",
    "0.0.0.0 tumblr.com",
    "0.0.0.0 www.tumblr.com",
    "0.0.0.0 telegram.org",
    "0.0.0.0 web.telegram.org",
    "0.0.0.0 t.me",
    "0.0.0.0 croxyproxy.com",
    "0.0.0.0 www.croxyproxy.com",
    "0.0.0.0 proxysite.com",
    "0.0.0.0 hide.me",
    "0.0.0.0 blockaway.net"
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

# 4. Apply Registry Policies (Chrome, Edge, ForceYouTubeRestrict=0, Proxy Direct Lock, VPN & Notorious Domains Block)
Write-Host "`n4. Applying Registry Policies (Browser Policies, YouTube Comments Unlocked & Proxy Direct Lock)..." -ForegroundColor Yellow
$regPath = "d:\Ai studio\DpcLocker + Windows incognito Blocker\enable_windows_protection.reg"
reg import "$regPath"
Write-Host "   [+] Applied Chrome, Edge & Windows Registry Policies (YouTube Comments Enabled)" -ForegroundColor Green

# 5. Stop and Disable Windows RasMan Service (Built-in VPN)
Write-Host "`n5. Disabling Windows VPN Service (RasMan)..." -ForegroundColor Yellow
Stop-Service -Name "RasMan" -Force -ErrorAction SilentlyContinue
Set-Service -Name "RasMan" -StartupType Disabled -ErrorAction SilentlyContinue
Write-Host "   [+] Disabled Remote Access Connection Manager (Windows VPN)" -ForegroundColor Green

# 6. Flush DNS Cache and Reset Browser Sockets
Write-Host "`n6. Flushing DNS Cache & Resetting Browser Connections..." -ForegroundColor Yellow
Clear-DnsClientCache
Stop-Process -Name "chrome" -Force -ErrorAction SilentlyContinue
Stop-Process -Name "msedge" -Force -ErrorAction SilentlyContinue
Write-Host "   [+] DNS Cache flushed and browser processes reset successfully!" -ForegroundColor Green

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "  DOMAIN & ADULT PROTECTION IS NOW ACTIVE ON WINDOWS!" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
