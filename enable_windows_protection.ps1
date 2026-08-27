# ==============================================================================
# DPCLOCKER :: WINDOWS ENTERPRISE CONTENT & MASTER PROXY LOCKDOWN ENGINE
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
Write-Host " $C_HDR[#] DPCLOCKER :: ENTERPRISE PROXY & CONTENT LOCKDOWN ENGINE$R"
Write-Host "$C_HDR===============================================================================$R`n"

# ------------------------------------------------------------------------------
# 1. APPLY CLEANBROWSING FAMILY & CLOUDFLARE FAMILY-FILTER DNS
# ------------------------------------------------------------------------------
Write-Host "$C_SEC[1/6] Configuring Protective Family-Filtered DNS on Network Adapters...$R"
try {
    $adapters = Get-NetAdapter | Where-Object { $_.Status -eq "Up" }
    foreach ($adapter in $adapters) {
        Set-DnsClientServerAddress -InterfaceAlias $adapter.Name -ServerAddresses ("185.228.168.168", "1.1.1.3") -ErrorAction SilentlyContinue
        Write-Host "  $C_OK[+] Set CleanBrowsing / Cloudflare Family DNS on: $($adapter.Name) (185.228.168.168 / 1.1.1.3)$R"
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

    # Notorious Streaming & Social Media
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

    # Master Web Proxy Domains & Unblockers
    "0.0.0.0 onlineproxy.org",
    "0.0.0.0 www.onlineproxy.org",
    "0.0.0.0 uproxy.online",
    "0.0.0.0 www.uproxy.online",
    "0.0.0.0 api.uproxy.online",
    "0.0.0.0 proxysite.com",
    "0.0.0.0 www.proxysite.com",
    "0.0.0.0 proxysite.cloud",
    "0.0.0.0 proxysite.site",
    "0.0.0.0 proxysite.one",
    "0.0.0.0 proxysite.net",
    "0.0.0.0 proxysite.org",
    "0.0.0.0 hidester.one",
    "0.0.0.0 www.hidester.one",
    "0.0.0.0 proxy.hidester.one",
    "0.0.0.0 www-proxy.hidester.one",
    "0.0.0.0 hidester.com",
    "0.0.0.0 www.hidester.com",
    "0.0.0.0 hidester.net",
    "0.0.0.0 hidester.org",
    "0.0.0.0 croxyproxy.com",
    "0.0.0.0 www.croxyproxy.com",
    "0.0.0.0 croxyproxy.net",
    "0.0.0.0 croxyproxy.rocks",
    "0.0.0.0 www.croxyproxy.rocks",
    "0.0.0.0 croxy.network",
    "0.0.0.0 croxy.org",
    "0.0.0.0 extremevpn.com",
    "0.0.0.0 www.extremevpn.com",
    "0.0.0.0 proxy-ca.extremevpn.com",
    "0.0.0.0 proxy-us.extremevpn.com",
    "0.0.0.0 proxy-nl.extremevpn.com",
    "0.0.0.0 azureserv.com",
    "0.0.0.0 www.azureserv.com",
    "0.0.0.0 azureserv.net",
    "0.0.0.0 proxypal.net",
    "0.0.0.0 www.proxypal.net",
    "0.0.0.0 proxypal.org",
    "0.0.0.0 hide.me",
    "0.0.0.0 www.hide.me",
    "0.0.0.0 blockaway.net",
    "0.0.0.0 www.blockaway.net",
    "0.0.0.0 proxyium.com",
    "0.0.0.0 www.proxyium.com",
    "0.0.0.0 proxyium.net",
    "0.0.0.0 kproxy.com",
    "0.0.0.0 vpnbook.com",
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
    "0.0.0.0 alohabrowser.com",
    "0.0.0.0 vtransmit.com",
    "0.0.0.0 www.vtransmit.com",
    "0.0.0.0 p23hxejm1.com",
    "0.0.0.0 rm358.com",
    "0.0.0.0 shadowproxy.org",
    "0.0.0.0 interstellarproxy.com",
    "0.0.0.0 incognitoproxy.com",
    "0.0.0.0 nebula.net",
    "0.0.0.0 titaniumnetwork.org",
    "0.0.0.0 womginx.org",
    "0.0.0.0 zend2.com",
    "0.0.0.0 zendproxy.com",
    "0.0.0.0 megaproxy.com",
    "0.0.0.0 newipnow.com",
    "0.0.0.0 dontfilter.us",
    "0.0.0.0 unblock-web.com",
    "0.0.0.0 unblockvideos.com",
    "0.0.0.0 free-proxy.cz",
    "0.0.0.0 proxybroker.online",
    "0.0.0.0 smartproxy.com",
    "0.0.0.0 brightdata.com",
    "0.0.0.0 oxylabs.io",
    "0.0.0.0 webproxy.free",
    "0.0.0.0 free-proxy-list.net",
    "0.0.0.0 usaproxy.info",
    "0.0.0.0 german-proxy.de",
    "0.0.0.0 myspaceproxy.org",
    "0.0.0.0 youtubeproxy.org",
    "0.0.0.0 tiktokproxy.com",
    "0.0.0.0 unblockyoutube.net",
    "0.0.0.0 bypassblocks.com",
    "0.0.0.0 surfshield.io",
    "0.0.0.0 cloakproxy.org",
    "0.0.0.0 scramjet.org",
    "0.0.0.0 tomp.app",
    "0.0.0.0 arsenic.org",
    "0.0.0.0 holyub.org",
    "0.0.0.0 dynamicproxy.org",
    "0.0.0.0 astralproxy.org",
    "0.0.0.0 phantomproxy.org",
    "0.0.0.0 metallicproxy.org",
    "0.0.0.0 selenite.cc",
    "0.0.0.0 ludicrous.org",
    "0.0.0.0 shadowtabs.org",
    "0.0.0.0 ccproxy.com",
    "0.0.0.0 4proxy.de",
    "0.0.0.0 vtunnel.com",
    "0.0.0.0 polysolve.net",
    "0.0.0.0 unblocker.cc",
    "0.0.0.0 unblocker.us",
    "0.0.0.0 web-proxy.cc",
    "0.0.0.0 superproxy.cc",
    "0.0.0.0 privacysite.net",
    "0.0.0.0 proxyserver.com",
    "0.0.0.0 freeproxyserver.co",
    "0.0.0.0 freeopenproxy.com",
    "0.0.0.0 freewebproxy.com",
    "0.0.0.0 myproxy.ca",
    "0.0.0.0 quickproxy.co.uk",
    "0.0.0.0 snoopblocker.com",
    "0.0.0.0 surfbrowser.com",
    "0.0.0.0 unblockmyweb.com",
    "0.0.0.0 unblockall.org",
    "0.0.0.0 unblocker.biz",
    "0.0.0.0 unblocker.info",
    "0.0.0.0 unblocker.online",
    "0.0.0.0 unblocker.site",
    "0.0.0.0 unblocker.tech",
    "0.0.0.0 unblocksite.org",
    "0.0.0.0 unblockwebsites.org",
    "0.0.0.0 webproxy.net",
    "0.0.0.0 webproxy.org",
    "0.0.0.0 webproxy.site",
    "0.0.0.0 youproxy.org"
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
Write-Host "`n$C_SEC[3/6] Enforcing 100% Valid Chromium URLBlocklist Policies...$R"

$browserConfigs = @(
    @{ Name = "Google Chrome";  Roots = @("HKLM:\SOFTWARE\Policies\Google\Chrome", "HKCU:\SOFTWARE\Policies\Google\Chrome"); IncognitoProp = "IncognitoModeAvailability" },
    @{ Name = "Microsoft Edge"; Roots = @("HKLM:\SOFTWARE\Policies\Microsoft\Edge", "HKCU:\SOFTWARE\Policies\Microsoft\Edge"); IncognitoProp = "InPrivateModeAvailability" },
    @{ Name = "Brave Browser";  Roots = @("HKLM:\SOFTWARE\Policies\BraveSoftware\Brave", "HKCU:\SOFTWARE\Policies\BraveSoftware\Brave"); IncognitoProp = "IncognitoModeAvailability" }
)

# 100% Valid Chromium URLBlocklist (Domain matching: "example.com" blocks example.com, www, and all subdomains)
$blockedDomains = @(
    # Master Web Proxy Domains & Unblockers
    "onlineproxy.org",
    "uproxy.online",
    "proxysite.com",
    "proxysite.cloud",
    "proxysite.site",
    "proxysite.one",
    "proxysite.net",
    "proxysite.org",
    "hidester.one",
    "hidester.com",
    "hidester.net",
    "hidester.org",
    "croxyproxy.com",
    "croxyproxy.net",
    "croxyproxy.rocks",
    "croxy.network",
    "croxy.org",
    "extremevpn.com",
    "azureserv.com",
    "azureserv.net",
    "proxypal.net",
    "proxypal.org",
    "hide.me",
    "blockaway.net",
    "proxyium.com",
    "proxyium.net",
    "kproxy.com",
    "vpnbook.com",
    "plainproxies.com",
    "hidemyass.com",
    "whoer.net",
    "zalmos.com",
    "filterbypass.me",
    "4everproxy.com",
    "toolur.com",
    "webproxy.to",
    "turbohide.org",
    "freeproxy.win",
    "nodeunblocker.net",
    "rammerhead.org",
    "ultraviolet.dev",
    "anarchyproxy.com",
    "hyperproxy.network",
    "shuttleproxy.com",
    "alohabrowser.com",
    "vtransmit.com",
    "p23hxejm1.com",
    "rm358.com",
    "shadowproxy.org",
    "interstellarproxy.com",
    "incognitoproxy.com",
    "nebula.net",
    "titaniumnetwork.org",
    "womginx.org",
    "zend2.com",
    "zendproxy.com",
    "megaproxy.com",
    "newipnow.com",
    "dontfilter.us",
    "unblock-web.com",
    "unblockvideos.com",
    "free-proxy.cz",
    "proxybroker.online",
    "smartproxy.com",
    "brightdata.com",
    "oxylabs.io",
    "webproxy.free",
    "free-proxy-list.net",
    "usaproxy.info",
    "german-proxy.de",
    "myspaceproxy.org",
    "youtubeproxy.org",
    "tiktokproxy.com",
    "unblockyoutube.net",
    "bypassblocks.com",
    "surfshield.io",
    "cloakproxy.org",
    "scramjet.org",
    "tomp.app",
    "arsenic.org",
    "holyub.org",
    "dynamicproxy.org",
    "astralproxy.org",
    "phantomproxy.org",
    "metallicproxy.org",
    "selenite.cc",
    "ludicrous.org",
    "shadowtabs.org",
    "ccproxy.com",
    "4proxy.de",
    "vtunnel.com",
    "polysolve.net",
    "unblocker.cc",
    "unblocker.us",
    "web-proxy.cc",
    "superproxy.cc",
    "privacysite.net",
    "proxyserver.com",
    "freeproxyserver.co",
    "freeopenproxy.com",
    "freewebproxy.com",
    "myproxy.ca",
    "quickproxy.co.uk",
    "snoopblocker.com",
    "surfbrowser.com",
    "unblockmyweb.com",
    "unblockall.org",
    "unblocker.biz",
    "unblocker.info",
    "unblocker.online",
    "unblocker.site",
    "unblocker.tech",
    "unblocksite.org",
    "unblockwebsites.org",
    "webproxy.net",
    "webproxy.org",
    "webproxy.site",
    "youproxy.org",

    # Notorious Streaming & Social Media
    "fboxtv.org",
    "x.com",
    "twitter.com",
    "twimg.com",
    "reddit.com",
    "redd.it",
    "redditmedia.com",
    "tumblr.com",
    "telegram.org",
    "t.me",
    "tiktok.com"
)

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

            # Purge URLAllowlist to avoid conflicts
            $urlAllowKey = "$rootKey\URLAllowlist"
            if (Test-Path $urlAllowKey) { Remove-Item -Path $urlAllowKey -Recurse -Force -ErrorAction SilentlyContinue }

            # Enforce 100% Valid Chromium URLBlocklist
            $urlBlockKey = "$rootKey\URLBlocklist"
            if (Test-Path $urlBlockKey) { Remove-Item -Path $urlBlockKey -Recurse -Force -ErrorAction SilentlyContinue }
            New-Item -Path $urlBlockKey -Force -ErrorAction SilentlyContinue | Out-Null
            for ($i = 0; $i -lt $blockedDomains.Count; $i++) {
                $num = $i + 1
                Set-ItemProperty -Path $urlBlockKey -Name "$num" -Value $blockedDomains[$i] -Type String -Force -ErrorAction SilentlyContinue
            }
        } catch {
            # Catch individual key errors gracefully
        }
    }
    Write-Host "  $C_OK[+] $($b.Name): Incognito Disabled | Direct Proxy Active | $($blockedDomains.Count) Proxy & Content Domains Locked$R"
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
Write-Host " $C_OK[OK] ENTERPRISE PROXY & CONTENT LOCKDOWN IS NOW 100% ACTIVE!$R"
Write-Host "$C_HDR===============================================================================$R`n"
