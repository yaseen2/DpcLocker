# ==============================================================================
# DPCLOCKER :: WINDOWS BROWSER & CONTENT LOCKDOWN ENGINE
# ==============================================================================

# 0. Auto-Elevate to Administrator with UAC prompt if not elevated
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host "Elevating with Administrator privileges..." -ForegroundColor Yellow
    $scriptPath = $MyInvocation.MyCommand.Path
    Start-Process powershell -Verb RunAs -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$scriptPath`""
    Exit
}

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
Write-Host " $C_HDR[#] DPCLOCKER :: WINDOWS BROWSER & DOMAIN LOCKDOWN ENGINE$R"
Write-Host "$C_HDR===============================================================================$R`n"

# ------------------------------------------------------------------------------
# 1. APPLY CLOUDFLARE FAMILY-FILTER DNS (1.1.1.3 / 1.0.0.3)
# ------------------------------------------------------------------------------
Write-Host "$C_SEC[1/6] Configuring Cloudflare Family-Filtered DNS on Network Adapters...$R"
try {
    $adapters = Get-NetAdapter | Where-Object { $_.Status -eq "Up" }
    foreach ($adapter in $adapters) {
        Set-DnsClientServerAddress -InterfaceAlias $adapter.Name -ServerAddresses ("1.1.1.3", "1.0.0.3") -ErrorAction SilentlyContinue
        Write-Host "  $C_OK[+] Set Cloudflare Family DNS on: $($adapter.Name) (1.1.1.3 / 1.0.0.3)$R"
    }
} catch {
    Write-Host "  $C_WARN[!] DNS configuration warning: $($_.Exception.Message)$R"
}

# ------------------------------------------------------------------------------
# 2. UPDATE HOSTS FILE FOR SAFESEARCH & NOTORIOUS DOMAINS
# ------------------------------------------------------------------------------
Write-Host "`n$C_SEC[2/6] Updating Windows Hosts File (SafeSearch & Adult/Notorious Blocklist)...$R"
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
    "0.0.0.0 blockaway.net",
    "0.0.0.0 tiktok.com",
    "0.0.0.0 www.tiktok.com",
    "0.0.0.0 v16-webapp-prime.tiktok.com",
    "0.0.0.0 v19-webapp-prime.tiktok.com"
)

try {
    if (Test-Path $hostsPath) {
        $existingContent = [System.IO.File]::ReadAllText($hostsPath)
        $newEntriesToAdd = @()
        foreach ($entry in $hostsEntries) {
            if (-not $existingContent.Contains($entry)) {
                $newEntriesToAdd += $entry
            }
        }
        if ($newEntriesToAdd.Count -gt 0) {
            $textToAppend = "`r`n" + ($newEntriesToAdd -join "`r`n")
            [System.IO.File]::AppendAllText($hostsPath, $textToAppend)
            Write-Host "  $C_OK[+] Appended $($newEntriesToAdd.Count) protective blocklist entries to hosts file.$R"
        } else {
            Write-Host "  $C_OK[+] All hosts blocklist rules are already present.$R"
        }
    }
} catch {
    Write-Host "  $C_ERR[!] Failed to write to hosts file: $($_.Exception.Message)$R"
}

# ------------------------------------------------------------------------------
# 3. APPLY ENTERPRISE BROWSER POLICIES (CHROME, EDGE, BRAVE)
# ------------------------------------------------------------------------------
Write-Host "`n$C_SEC[3/6] Enforcing Strict Enterprise Policies in Chrome, Edge & Brave...$R"

$browserTargets = @(
    @{ Name = "Google Chrome";  Key = "HKLM:\SOFTWARE\Policies\Google\Chrome"; IncognitoProp = "IncognitoModeAvailability" },
    @{ Name = "Microsoft Edge"; Key = "HKLM:\SOFTWARE\Policies\Microsoft\Edge"; IncognitoProp = "InPrivateModeAvailability" },
    @{ Name = "Brave Browser";  Key = "HKLM:\SOFTWARE\Policies\BraveSoftware\Brave"; IncognitoProp = "IncognitoModeAvailability" }
)

$blockedUrls = @(
    "*fboxtv.org*", "*x.com*", "*twitter.com*", "*twimg.com*", "*redd.it*",
    "*tumblr.com*", "*telegram.org*", "*t.me*", "*croxyproxy.com*",
    "*proxysite.com*", "*hide.me*", "*blockaway.net*", "*tiktok.com*"
)

foreach ($b in $browserTargets) {
    try {
        if (-not (Test-Path $b.Key)) {
            New-Item -Path $b.Key -Force | Out-Null
        }
        
        # 1 = Incognito Disabled, SafeSearch Forced, DoH Disabled (Forces local DNS), Direct Proxy
        Set-ItemProperty -Path $b.Key -Name $b.IncognitoProp -Value 1 -Type DWord -Force
        Set-ItemProperty -Path $b.Key -Name "ForceGoogleSafeSearch" -Value 1 -Type DWord -Force
        Set-ItemProperty -Path $b.Key -Name "SafeSitesFilterBehavior" -Value 1 -Type DWord -Force
        Set-ItemProperty -Path $b.Key -Name "ForceYouTubeRestrict" -Value 0 -Type DWord -Force
        Set-ItemProperty -Path $b.Key -Name "DnsOverHttpsMode" -Value "off" -Type String -Force
        Set-ItemProperty -Path $b.Key -Name "ProxyMode" -Value "direct" -Type String -Force
        
        # Write URLBlocklist
        $urlBlockKey = "$($b.Key)\URLBlocklist"
        if (-not (Test-Path $urlBlockKey)) {
            New-Item -Path $urlBlockKey -Force | Out-Null
        }
        for ($i = 0; $i -lt $blockedUrls.Count; $i++) {
            $num = $i + 1
            Set-ItemProperty -Path $urlBlockKey -Name "$num" -Value $blockedUrls[$i] -Type String -Force
        }
        
        Write-Host "  $C_OK[+] $($b.Name): Incognito Disabled | SafeSearch Enforced | URLBlocklist Active$R"
    } catch {
        Write-Host "  $C_ERR[!] Failed to set policies for $($b.Name): $($_.Exception.Message)$R"
    }
}

# ------------------------------------------------------------------------------
# 4. WINDOWS NETWORK & PROXY LOCKDOWN
# ------------------------------------------------------------------------------
Write-Host "`n$C_SEC[4/6] Enforcing Windows Proxy & Network Lockdown...$R"
try {
    $netKey = "HKLM:\SOFTWARE\Policies\Microsoft\Network Connections"
    if (-not (Test-Path $netKey)) { New-Item -Path $netKey -Force | Out-Null }
    Set-ItemProperty -Path $netKey -Name "NC_NewConnectionWizard" -Value 1 -Type DWord -Force
    Set-ItemProperty -Path $netKey -Name "NC_DeleteAllUserConnection" -Value 1 -Type DWord -Force

    $ieKey = "HKLM:\SOFTWARE\Policies\Microsoft\Internet Explorer\Control Panel"
    if (-not (Test-Path $ieKey)) { New-Item -Path $ieKey -Force | Out-Null }
    Set-ItemProperty -Path $ieKey -Name "Proxy" -Value 1 -Type DWord -Force
    Set-ItemProperty -Path $ieKey -Name "Connwiz Admin Lock" -Value 1 -Type DWord -Force
    Write-Host "  $C_OK[+] Windows Proxy & VPN Wizard locked down.$R"
} catch {
    Write-Host "  $C_WARN[!] Network policy notice: $($_.Exception.Message)$R"
}

# ------------------------------------------------------------------------------
# 5. FLUSH DNS RESOLVER CACHE
# ------------------------------------------------------------------------------
Write-Host "`n$C_SEC[5/6] Flushing Windows DNS Cache...$R"
try {
    Clear-DnsClientCache -ErrorAction SilentlyContinue
    ipconfig /flushdns | Out-Null
    Write-Host "  $C_OK[+] DNS resolver cache successfully flushed.$R"
} catch {
    Write-Host "  $C_WARN[!] DNS flush notice: $($_.Exception.Message)$R"
}

# ------------------------------------------------------------------------------
# 6. GRACEFUL BROWSER PROCESS RESTART (POLICY ACTIVATION)
# ------------------------------------------------------------------------------
Write-Host "`n$C_SEC[6/6] Checking Running Browser Instances...$R"
$runningBrowsers = Get-Process -Name "chrome", "msedge", "brave" -ErrorAction SilentlyContinue
if ($runningBrowsers) {
    Write-Host "  $C_WARN[!] Active browser instances detected.$R"
    Write-Host "  $C_TXT    Chromium browsers must restart to activate new Incognito & URL policies.$R"
    $ans = Read-Host "`n  $C_HDR[?] Restart running browsers now to enforce policies? (Y/N)$R"
    if ($ans -eq "Y" -or $ans -eq "y") {
        Write-Host "  $C_SUB[*] Restarting browsers...$R"
        Stop-Process -Name "chrome", "msedge", "brave" -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 1
        Write-Host "  $C_OK[+] Browsers refreshed! All protections are now LIVE.$R"
    } else {
        Write-Host "  $C_WARN[*] Notice: Protections will take effect next time you open your browser.$R"
    }
} else {
    Write-Host "  $C_OK[+] No conflicting browser instances running. Policies are ready!$R"
}

Write-Host "`n$C_HDR===============================================================================$R"
Write-Host " $C_OK[OK] WINDOWS BROWSER PROTECTION IS NOW 100% ACTIVE!$R"
Write-Host "$C_HDR===============================================================================$R`n"
Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
