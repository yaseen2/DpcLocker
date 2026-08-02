# Requires Administrator Privilege
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host "Please run this PowerShell script as Administrator!" -ForegroundColor Red
    Exit
}

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  Enabling Windows Adult Content Protection (Lean Setup)" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

# 1. Apply CleanBrowsing Family DNS to all Active Network Adapters
Write-Host "`n1. Configuring Family-Filter DNS on Network Adapters..." -ForegroundColor Yellow
$adapters = Get-NetAdapter | Where-Object { $_.Status -eq "Up" }
foreach ($adapter in $adapters) {
    Set-DnsClientServerAddress -InterfaceAlias $adapter.Name -ServerAddresses ("185.228.168.168", "185.228.169.168")
    Write-Host "   [+] Set CleanBrowsing Family DNS on: $($adapter.Name)" -ForegroundColor Green
}

# 2. Update Hosts File for Google & Bing SafeSearch
Write-Host "`n2. Updating System Hosts File for Google & Bing SafeSearch..." -ForegroundColor Yellow
$hostsPath = "$env:SystemRoot\System32\drivers\etc\hosts"
$hostsEntries = @(
    "216.239.38.120 www.google.com",
    "216.239.38.120 google.com",
    "216.239.38.120 forcesafesearch.google.com",
    "216.239.38.120 www.bing.com",
    "216.239.38.120 strict.bing.com"
)

# Read hosts file completely into memory to prevent file handle locking
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
    Write-Host "   [+] SafeSearch hosts entries are already present." -ForegroundColor Green
}

# 3. Apply Chrome & Edge Registry Policies
Write-Host "`n3. Applying Chrome & Edge Registry Policies..." -ForegroundColor Yellow
$regPath = "d:\Ai studio\DpcLocker + Windows incognito Blocker\enable_windows_protection.reg"
reg import "$regPath"
Write-Host "   [+] Applied Incognito & SafeSearch Registry Policies" -ForegroundColor Green

# 4. Flush DNS Cache
Write-Host "`n4. Flushing DNS Cache..." -ForegroundColor Yellow
Clear-DnsClientCache
Write-Host "   [+] DNS Cache flushed successfully!" -ForegroundColor Green

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "  ADULT CONTENT PROTECTION IS NOW ACTIVE ON WINDOWS!" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
