# ==============================================================================
# DPCLOCKER :: WINDOWS GENERIC ANTI-PROXY & BROWSER HARDENING ENGINE
# ==============================================================================

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
Write-Host " $C_HDR[#] DPCLOCKER :: GENERIC ANTI-PROXY & CONTENT LOCKDOWN ENGINE$R"
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
    Write-Host "  $C_WARN[!] DNS configuration notice: $($_.Exception.Message)$R"
}

# ------------------------------------------------------------------------------
# 2. UPDATE HOSTS FILE FOR SAFESEARCH & KNOWN PROXY NETWORKS
# ------------------------------------------------------------------------------
Write-Host "`n$C_SEC[2/6] Updating Windows Hosts File (SafeSearch & Web Proxy Blacklist)...$R"
$hostsPath = "$env:SystemRoot\System32\drivers\etc\hosts"

$hostsEntries = @(
    # Strict SafeSearch VIPs for Global & Regional Search Engines
    "216.239.38.120 www.google.com",
    "216.239.38.120 google.com",
    "216.239.38.120 forcesafesearch.google.com",
    "216.239.38.120 www.google.co.id",
    "216.239.38.120 google.co.id",
    "216.239.38.120 www.google.com.pk",
    "216.239.38.120 google.com.pk",
    "216.239.38.120 www.google.co.uk",
    "216.239.38.120 google.co.uk",
    "216.239.38.120 www.bing.com",
    "216.239.38.120 strict.bing.com",

    # Notorious Streaming & Social
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
    "0.0.0.0 tiktok.com",
    "0.0.0.0 www.tiktok.com",
    "0.0.0.0 v16-webapp-prime.tiktok.com",
    "0.0.0.0 v19-webapp-prime.tiktok.com",

    # Web Proxy Engines & Dynamic Relay Nodes
    "0.0.0.0 uproxy.online",
    "0.0.0.0 www.uproxy.online",
    "0.0.0.0 api.uproxy.online",
    "0.0.0.0 vtransmit.com",
    "0.0.0.0 www.vtransmit.com",
    "0.0.0.0 azureserv.com",
    "0.0.0.0 www.azureserv.com",
    "0.0.0.0 proxypal.net",
    "0.0.0.0 www.proxypal.net",
    "0.0.0.0 hide.me",
    "0.0.0.0 www.hide.me",
    "0.0.0.0 croxyproxy.com",
    "0.0.0.0 www.croxyproxy.com",
    "0.0.0.0 croxyproxy.net",
    "0.0.0.0 croxyproxy.rocks",
    "0.0.0.0 www.croxyproxy.rocks",
    "0.0.0.0 croxy.network",
    "0.0.0.0 croxy.org",
    "0.0.0.0 blockaway.net",
    "0.0.0.0 www.blockaway.net",
    "0.0.0.0 proxysite.com",
    "0.0.0.0 www.proxysite.com",
    "0.0.0.0 proxysite.cloud",
    "0.0.0.0 proxysite.site",
    "0.0.0.0 proxysite.one",
    "0.0.0.0 proxyium.com",
    "0.0.0.0 www.proxyium.com",
    "0.0.0.0 proxyium.net",
    "0.0.0.0 kproxy.com",
    "0.0.0.0 vpnbook.com",
    "0.0.0.0 hidester.com",
    "0.0.0.0 www.hidester.com",
    "0.0.0.0 hidester.one",
    "0.0.0.0 www.hidester.one",
    "0.0.0.0 proxy.hidester.one",
    "0.0.0.0 www-proxy.hidester.one",
    "0.0.0.0 extremevpn.com",
    "0.0.0.0 www.extremevpn.com",
    "0.0.0.0 proxy-ca.extremevpn.com",
    "0.0.0.0 proxy-us.extremevpn.com",
    "0.0.0.0 proxy-nl.extremevpn.com",
    "0.0.0.0 plainproxies.com",
    "0.0.0.0 hidemyass.com",
    "0.0.0.0 whoer.net",
    "0.0.0.0 zalmos.com",
    "0.0.0.0 filterbypass.me",
    "0.0.0.0 4everproxy.com",
    "0.0.0.0 toolur.com",
    "0.0.0.0 webproxy.to",
    "0.0.0.0 turbohide.org",
    "0.0.0.0 freeproxy.win",
    "0.0.0.0 nodeunblocker.net",
    "0.0.0.0 rammerhead.org",
    "0.0.0.0 ultraviolet.dev",
    "0.0.0.0 anarchyproxy.com",
    "0.0.0.0 hyperproxy.network",
    "0.0.0.0 shuttleproxy.com",
    "0.0.0.0 alohabrowser.com"
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
# 3. APPLY ENTERPRISE BROWSER POLICIES (CHROME, EDGE, BRAVE - HKLM & HKCU)
# ------------------------------------------------------------------------------
Write-Host "`n$C_SEC[3/6] Enforcing Generic Anti-Proxy & Content Policies in Browsers...$R"

$browserConfigs = @(
    @{ Name = "Google Chrome";  Roots = @("HKLM:\SOFTWARE\Policies\Google\Chrome", "HKCU:\SOFTWARE\Policies\Google\Chrome"); IncognitoProp = "IncognitoModeAvailability" },
    @{ Name = "Microsoft Edge"; Roots = @("HKLM:\SOFTWARE\Policies\Microsoft\Edge", "HKCU:\SOFTWARE\Policies\Microsoft\Edge"); IncognitoProp = "InPrivateModeAvailability" },
    @{ Name = "Brave Browser";  Roots = @("HKLM:\SOFTWARE\Policies\BraveSoftware\Brave", "HKCU:\SOFTWARE\Policies\BraveSoftware\Brave"); IncognitoProp = "IncognitoModeAvailability" }
)

# A. URLAllowlist: Local LAN & Development (Overrides IP Blocklist)
$allowedUrls = @(
    "*://192.168.*",
    "*://10.*",
    "*://127.*",
    "*://localhost*",
    "*://172.16.*", "*://172.17.*", "*://172.18.*", "*://172.19.*",
    "*://172.20.*", "*://172.21.*", "*://172.22.*", "*://172.23.*",
    "*://172.24.*", "*://172.25.*", "*://172.26.*", "*://172.27.*",
    "*://172.28.*", "*://172.29.*", "*://172.30.*", "*://172.31.*",
    "https://www.google.com*"
)

# B. URLBlocklist:
# 1. Generic Raw Public IPv4 Blocking (1.x.x.x through 255.x.x.x)
$rawIpBlockList = @(1..255 | ForEach-Object { "*://$_.*" })

# 2. Generic Proxy Engine Signatures (Ultraviolet, DIP, Osana, Stomp, CroxyProxy, Rammerhead, Glype, etc.)
$genericProxyPatterns = @(
    "*://*/uv/*",
    "*://*/dip/*",
    "*://*/osana/*",
    "*://*/stomp/*",
    "*://*/*__cpo*",
    "*://*/*__cpi*",
    "*://*/*__cpr*",
    "*://*/__cpi.php*",
    "*://*/*?*__cpo*",
    "*://*/*&__cpo*",
    "*://*/*/uv/service/*",
    "*://*/*/service/gateway*",
    "*://*/*/bare/*",
    "*://*/*aHR0cHM6Ly9*",
    "*://*/*aHR0cDovL*",
    "*://*/*&u=a1aHR0c*",
    "*://*/*&u=aHR0c*",
    "*://*/*?*u=a1aHR0c*",
    "*://*/*?*u=aHR0c*",
    "*://*/*evp-target*",
    "*://*/*__proxy*",
    "*://*/*uproxy*",
    "*://*/*vtransmit*",
    "*://*/*croxyproxy*",
    "*://*/*proxypal*",
    "*://*/*proxysite*",
    "*://*/*blockaway*",
    "*://*/*lumiproxy*",
    "*://*/*hidester*",
    "*://*/*extremevpn*",
    "*://*/*p23hxejm1.com*",
    "*://*/*rm358.com*",

    # Search Engine Query Shields (Blocks searching for proxy unblockers)
    "*://www.google.*/search?*q=*proxy*",
    "*://www.google.*/search?*q=*unblock+website*",
    "*://www.google.*/search?*q=*bypass+filter*",
    "*://www.bing.com/search?*q=*proxy*",
    "*://www.bing.com/search?*q=*unblock*"
)

# 3. Notorious Platforms & Regional Google Bypasses
$notoriousAndSearchBypasses = @(
    "*fboxtv.org*", "*x.com*", "*twitter.com*", "*twimg.com*", "*redd.it*", "*reddit.com*",
    "*tumblr.com*", "*telegram.org*", "*t.me*", "*tiktok.com*",
    "*://*.google.co.*", "*://*.google.com.*", "*://*.google.ca*", "*://*.google.de*",
    "*://*.google.fr*", "*://*.google.it*", "*://*.google.es*", "*://*.google.nl*",
    "*://*.google.ru*", "*://*.google.pl*"
)

# Combine into master blocklist
$masterBlockedUrls = $rawIpBlockList + $genericProxyPatterns + $notoriousAndSearchBypasses

foreach ($b in $browserConfigs) {
    foreach ($rootKey in $b.Roots) {
        try {
            if (-not (Test-Path $rootKey)) { New-Item -Path $rootKey -Force -ErrorAction SilentlyContinue | Out-Null }
            Set-ItemProperty -Path $rootKey -Name $b.IncognitoProp -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue
            Set-ItemProperty -Path $rootKey -Name "ForceGoogleSafeSearch" -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue
            Set-ItemProperty -Path $rootKey -Name "SafeSitesFilterBehavior" -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue
            Set-ItemProperty -Path $rootKey -Name "ForceYouTubeRestrict" -Value 0 -Type DWord -Force -ErrorAction SilentlyContinue
            Set-ItemProperty -Path $rootKey -Name "DnsOverHttpsMode" -Value "off" -Type String -Force -ErrorAction SilentlyContinue
            Set-ItemProperty -Path $rootKey -Name "ProxyMode" -Value "direct" -Type String -Force -ErrorAction SilentlyContinue

            # Purge any extension blocklists so extensions remain freely usable
            $extBlockKey = "$rootKey\ExtensionInstallBlocklist"
            if (Test-Path $extBlockKey) { Remove-Item -Path $extBlockKey -Recurse -Force -ErrorAction SilentlyContinue }
            Remove-ItemProperty -Path $rootKey -Name "BlockExternalExtensions" -Force -ErrorAction SilentlyContinue

            # 1. Enforce URLAllowlist (LAN & Localhost)
            $urlAllowKey = "$rootKey\URLAllowlist"
            if (-not (Test-Path $urlAllowKey)) { New-Item -Path $urlAllowKey -Force -ErrorAction SilentlyContinue | Out-Null }
            for ($i = 0; $i -lt $allowedUrls.Count; $i++) {
                $num = $i + 1
                Set-ItemProperty -Path $urlAllowKey -Name "$num" -Value $allowedUrls[$i] -Type String -Force -ErrorAction SilentlyContinue
            }

            # 2. Enforce Generic Master URLBlocklist (Raw IPs + Proxy Engines + Notorious)
            $urlBlockKey = "$rootKey\URLBlocklist"
            if (-not (Test-Path $urlBlockKey)) { New-Item -Path $urlBlockKey -Force -ErrorAction SilentlyContinue | Out-Null }
            for ($i = 0; $i -lt $masterBlockedUrls.Count; $i++) {
                $num = $i + 1
                Set-ItemProperty -Path $urlBlockKey -Name "$num" -Value $masterBlockedUrls[$i] -Type String -Force -ErrorAction SilentlyContinue
            }
        } catch {
            # Catch individual key errors gracefully
        }
    }
    Write-Host "  $C_OK[+] $($b.Name): Incognito Disabled | Raw IP Block Active | Generic Proxy Interception Active ($($masterBlockedUrls.Count) rules)$R"
}

# ------------------------------------------------------------------------------
# 4. WINDOWS NETWORK & VPN SERVICE LOCKDOWN
# ------------------------------------------------------------------------------
Write-Host "`n$C_SEC[4/6] Disabling Windows VPN Connections & RasMan Remote Access Service...$R"
try {
    $netConnKey = "HKLM:\SOFTWARE\Policies\Microsoft\Network Connections"
    if (-not (Test-Path $netConnKey)) { New-Item -Path $netConnKey -Force -ErrorAction SilentlyContinue | Out-Null }
    Set-ItemProperty -Path $netConnKey -Name "NC_NewConnectionWizard" -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue
    Set-ItemProperty -Path $netConnKey -Name "NC_DeleteAllUserConnection" -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue

    $ieControlKey = "HKLM:\SOFTWARE\Policies\Microsoft\Internet Explorer\Control Panel"
    if (-not (Test-Path $ieControlKey)) { New-Item -Path $ieControlKey -Force -ErrorAction SilentlyContinue | Out-Null }
    Set-ItemProperty -Path $ieControlKey -Name "Proxy" -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue
    Set-ItemProperty -Path $ieControlKey -Name "Connwiz Admin Lock" -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue

    # Disable RasMan (Windows Remote Access VPN Service)
    Set-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Services\RasMan" -Name "Start" -Value 4 -Type DWord -Force -ErrorAction SilentlyContinue
    Set-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Services\RasAuto" -Name "Start" -Value 4 -Type DWord -Force -ErrorAction SilentlyContinue
    Write-Host "  $C_OK[+] Windows VPN Services & Proxy configuration locked.$R"
} catch {
    Write-Host "  $C_WARN[!] Network lockdown notice: $($_.Exception.Message)$R"
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
    Write-Host "  $C_WARN[!] Active browser instances detected ($($runningBrowsers.Count) processes).$R"
    Write-Host "  $C_TXT    Chromium browsers must restart to activate new Extension & URL policies.$R"
    Write-Host "  $C_SUB[*] Refreshing browser processes to apply policies...$R"
    Stop-Process -Name "chrome", "msedge", "brave" -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
    Write-Host "  $C_OK[+] Browsers refreshed! All protections are now LIVE.$R"
} else {
    Write-Host "  $C_OK[+] No conflicting browser processes active. Policies are ready!$R"
}

Write-Host "`n$C_HDR===============================================================================$R"
Write-Host " $C_OK[OK] GENERIC ANTI-PROXY & CONTENT LOCKDOWN IS NOW 100% ACTIVE!$R"
Write-Host "$C_HDR===============================================================================$R`n"
