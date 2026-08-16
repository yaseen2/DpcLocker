$esc = [char]27
$C_HDR = "$esc[1;38;5;39m"
$C_SEC = "$esc[1;38;5;75m"
$C_TXT = "$esc[38;5;253m"
$C_OK  = "$esc[1;38;5;82m"
$C_WARN = "$esc[1;38;5;214m"
$C_ERR = "$esc[1;38;5;203m"
$C_SUB = "$esc[38;5;244m"
$R     = "$esc[0m"

Clear-Host
Write-Host "$C_HDR===============================================================================$R"
Write-Host " $C_HDR[#] DPCLOCKER :: WINDOWS BROWSER & NETWORK PROTECTION AUDIT$R"
Write-Host "$C_HDR===============================================================================$R`n"

# 1. Check Chrome
$chromeKey = "HKLM:\SOFTWARE\Policies\Google\Chrome"
$chromeIncog = (Get-ItemProperty -Path $chromeKey -Name "IncognitoModeAvailability" -ErrorAction SilentlyContinue).IncognitoModeAvailability
$chromeStatus = if ($chromeIncog -eq 1) { "$C_OK[√] ENFORCED (Incognito Blocked)$R" } else { "$C_ERR[!] NOT ENFORCED$R" }
Write-Host " $C_SEC[BROWSER INCOGNITO STATUS]$R"
Write-Host "   Google Chrome Incognito     : $chromeStatus"

# 2. Check Edge
$edgeKey = "HKLM:\SOFTWARE\Policies\Microsoft\Edge"
$edgeIncog = (Get-ItemProperty -Path $edgeKey -Name "InPrivateModeAvailability" -ErrorAction SilentlyContinue).InPrivateModeAvailability
$edgeStatus = if ($edgeIncog -eq 1) { "$C_OK[√] ENFORCED (InPrivate Blocked)$R" } else { "$C_ERR[!] NOT ENFORCED$R" }
Write-Host "   Microsoft Edge InPrivate    : $edgeStatus"

# 3. Check Brave
$braveKey = "HKLM:\SOFTWARE\Policies\BraveSoftware\Brave"
$braveIncog = (Get-ItemProperty -Path $braveKey -Name "IncognitoModeAvailability" -ErrorAction SilentlyContinue).IncognitoModeAvailability
$braveStatus = if ($braveIncog -eq 1) { "$C_OK[√] ENFORCED (Private Window Blocked)$R" } else { "$C_ERR[!] NOT ENFORCED$R" }
Write-Host "   Brave Browser Private Mode  : $braveStatus"

# 4. Check DNS
Write-Host "`n $C_SEC[NETWORK DNS FILTERS]$R"
$adapters = Get-NetAdapter | Where-Object { $_.Status -eq "Up" }
foreach ($a in $adapters) {
    $dns = (Get-DnsClientServerAddress -InterfaceAlias $a.Name -AddressFamily IPv4 -ErrorAction SilentlyContinue).ServerAddresses
    $dnsStr = if ($dns) { $dns -join ", " } else { "DHCP / Unset" }
    $isFamilyDns = ($dns -contains "1.1.1.3") -or ($dns -contains "185.228.168.168")
    $dnsBadge = if ($isFamilyDns) { "$C_OK[√] Cloudflare Family DNS Active$R" } else { "$C_WARN[!] Standard / Unfiltered DNS$R" }
    Write-Host "   Adapter: $($a.Name.PadRight(20)) : $dnsStr | $dnsBadge"
}

# 5. Check Hosts
Write-Host "`n $C_SEC[HOSTS DOMAIN BLOCKLIST]$R"
$hostsPath = "$env:SystemRoot\System32\drivers\etc\hosts"
if (Test-Path $hostsPath) {
    $hostsContent = [System.IO.File]::ReadAllText($hostsPath)
    $hasX = $hostsContent -match "twitter\.com|x\.com"
    $hasReddit = $hostsContent -match "reddit\.com"
    $hasTikTok = $hostsContent -match "tiktok\.com"
    $hasSafeSearch = $hostsContent -match "forcesafesearch"
    
    $xBadge = if ($hasX) { "$C_OK[√] Blocked$R" } else { "$C_ERR[!] Allowed$R" }
    $redditBadge = if ($hasReddit) { "$C_OK[√] Blocked$R" } else { "$C_ERR[!] Allowed$R" }
    $ttBadge = if ($hasTikTok) { "$C_OK[√] Blocked$R" } else { "$C_ERR[!] Allowed$R" }
    $ssBadge = if ($hasSafeSearch) { "$C_OK[√] Strict Enforced$R" } else { "$C_ERR[!] Standard$R" }

    Write-Host "   SafeSearch Google/Bing Host Redirect : $ssBadge"
    Write-Host "   X / Twitter Domain Filter            : $xBadge"
    Write-Host "   Reddit Domain Filter                 : $redditBadge"
    Write-Host "   TikTok Web Filter                    : $ttBadge"
}

# 6. Check Active Processes
Write-Host "`n $C_SEC[RUNNING BROWSER INSTANCES]$R"
$procs = Get-Process -Name "chrome", "msedge", "brave" -ErrorAction SilentlyContinue
if ($procs) {
    Write-Host "   $C_WARN[!] Active instances detected ($($procs.Count) processes). If you recently applied policies, restart browser to load new rules.$R"
} else {
    Write-Host "   $C_OK[√] No conflicting browser processes active.$R"
}

Write-Host "`n$C_HDR===============================================================================$R"
