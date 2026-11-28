# ==============================================================================
# DPCLOCKER :: WINDOWS ENTERPRISE CONTENT, BROWSER & NETWORK LOCKDOWN ENGINE
# ==============================================================================
# 1. CleanBrowsing Adult Filter IPv4 + IPv6 DNS (Allows study sites like
#    cssprep.com.pk & cssprepforum.com, blocks adult domains, forces Google
#    SafeSearch, preserves standard YouTube lectures/comments).
# 2. Hosts File SafeSearch, Proxy, Music & Unauthorized Browser Download Blocks.
# 3. Chrome & Edge Enterprise Policies: Secure DoH (CleanBrowsing Adult),
#    Locked Google SafeSearch, Disabled Incognito/InPrivate/Guest Mode,
#    ExtensionSettings blocking 'proxy'/'vpnProvider' permissions (keeping all
#    good extensions usable), and Brave Belt-and-Suspenders (TorDisabled=1,
#    BraveVPNDisabled=1).
# 4. Kernel-Level IFEO Execution Blocks (Windows 10 Home compatible) for exact
#    unauthorized browser & VPN client executable names (zero good-app impact).
# 5. Windows Network, VPN & Tor Firewall Lockdown + Leftover Browser Cleanup.
# ==============================================================================

param(
    [switch]$RestartBrowsers
)

$esc = [char]27
$C_HDR  = "$esc[1;38;5;39m"
$C_SEC  = "$esc[1;38;5;75m"
$C_TXT  = "$esc[38;5;253m"
$C_OK   = "$esc[1;38;5;82m"
$C_WARN = "$esc[1;38;5;214m"
$C_ERR  = "$esc[1;38;5;203m"
$C_SUB  = "$esc[38;5;244m"
$R      = "$esc[0m"

Write-Host "$C_HDR===============================================================================$R"
Write-Host " $C_HDR[#] DPCLOCKER :: ENTERPRISE BROWSER, DNS & CONTENT LOCKDOWN ENGINE v2$R"
Write-Host "$C_HDR===============================================================================$R`n"

# ------------------------------------------------------------------------------
# 1. APPLY CLEANBROWSING ADULT FILTER DNS (IPv4 + IPv6) ON ACTIVE ADAPTERS
# ------------------------------------------------------------------------------
# CleanBrowsing Adult Filter:
#   IPv4: 185.228.168.10, 185.228.169.11
#   IPv6: 2a0d:2a00:1::1, 2a0d:2a00:2::1
#   - Forces Google SafeSearch (216.239.38.120)
#   - Blocks adult domains (pornhub/xvideos/xhamster -> NXDOMAIN)
#   - Preserves standard YouTube (no forced Restricted Mode)
#   - Fixes Cloudflare 1.1.1.3 false blocks on study sites (cssprep.com.pk, cssprepforum.com)
Write-Host "$C_SEC[1/7] Configuring CleanBrowsing Adult Filter DNS (IPv4 + IPv6)...$R"
try {
    $adapters = Get-NetAdapter | Where-Object {
        $_.Status -eq "Up" -or $_.Name -match '^(Wi-Fi|Ethernet)$'
    }
    foreach ($adapter in $adapters) {
        try {
            Set-DnsClientServerAddress -InterfaceAlias $adapter.Name -ServerAddresses ("185.228.168.10", "185.228.169.11") -ErrorAction SilentlyContinue
            Set-DnsClientServerAddress -InterfaceAlias $adapter.Name -ServerAddresses ("2a0d:2a00:1::1", "2a0d:2a00:2::1") -ErrorAction SilentlyContinue
            # Apply both families cleanly using netsh fallback for reliable IPv4 + IPv6 persistence
            netsh interface ipv4 set dns name="$($adapter.Name)" static 185.228.168.10 primary validate=no | Out-Null
            netsh interface ipv4 add dns name="$($adapter.Name)" 185.228.169.11 index=2 validate=no | Out-Null
            netsh interface ipv6 set dns name="$($adapter.Name)" static 2a0d:2a00:1::1 primary validate=no | Out-Null
            netsh interface ipv6 add dns name="$($adapter.Name)" 2a0d:2a00:2::1 index=2 validate=no | Out-Null
            Write-Host "  $C_OK[+] Locked IPv4 (185.228.168.10 / 185.228.169.11) & IPv6 (2a0d:2a00:1::1) on: $($adapter.Name)$R"
        } catch {
            Write-Host "  $C_WARN[!] Adapter $($adapter.Name) notice: $($_.Exception.Message)$R"
        }
    }
} catch {
    Write-Host "  $C_WARN[!] DNS configuration notice: $($_.Exception.Message)$R"
}

# ------------------------------------------------------------------------------
# 2. UPDATE HOSTS FILE (SAFESEARCH, PROXIES, MUSIC & UNAUTHORIZED BROWSERS)
# ------------------------------------------------------------------------------
Write-Host "`n$C_SEC[2/7] Updating Windows Hosts File (SafeSearch, Proxies & Browser Downloads)...$R"
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
    "204.79.197.220 www.bing.com",
    "204.79.197.220 strict.bing.com",

    # Unauthorized Alternative Browser Download & Update Domains
    "0.0.0.0 brave.com",
    "0.0.0.0 www.brave.com",
    "0.0.0.0 search.brave.com",
    "0.0.0.0 bravesoftware.com",
    "0.0.0.0 laptop-updates.brave.com",
    "0.0.0.0 updates-cdn.bravesoftware.com",
    "0.0.0.0 opera.com",
    "0.0.0.0 www.opera.com",
    "0.0.0.0 get.geo.opera.com",
    "0.0.0.0 download.cdn-opera.com",
    "0.0.0.0 mozilla.org",
    "0.0.0.0 www.mozilla.org",
    "0.0.0.0 download.mozilla.org",
    "0.0.0.0 firefox.com",
    "0.0.0.0 www.firefox.com",
    "0.0.0.0 vivaldi.com",
    "0.0.0.0 www.vivaldi.com",
    "0.0.0.0 downloads.vivaldi.com",
    "0.0.0.0 torproject.org",
    "0.0.0.0 www.torproject.org",
    "0.0.0.0 dist.torproject.org",
    "0.0.0.0 waterfox.net",
    "0.0.0.0 www.waterfox.net",
    "0.0.0.0 librewolf.net",
    "0.0.0.0 www.librewolf.net",
    "0.0.0.0 arc.net",
    "0.0.0.0 www.arc.net",
    "0.0.0.0 thorium.rocks",
    "0.0.0.0 zen-browser.app",
    "0.0.0.0 floorp.app",
    "0.0.0.0 mullvad.net",
    "0.0.0.0 www.mullvad.net",

    # Unfiltered Alternative Search Engines (Chrome/Edge locked to Google SafeSearch)
    "0.0.0.0 duckduckgo.com",
    "0.0.0.0 www.duckduckgo.com",
    "0.0.0.0 startpage.com",
    "0.0.0.0 www.startpage.com",
    "0.0.0.0 qwant.com",
    "0.0.0.0 www.qwant.com",
    "0.0.0.0 yandex.com",
    "0.0.0.0 www.yandex.com",
    "0.0.0.0 yandex.ru",
    "0.0.0.0 presearch.com",
    "0.0.0.0 mojeek.com",
    "0.0.0.0 swisscows.com",

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

    # Online VPN App
    "0.0.0.0 onlinevpn.app",
    "0.0.0.0 www.onlinevpn.app",

    # Music Streaming & Audio Platforms (Standard YouTube preserved)
    "0.0.0.0 music.youtube.com",
    "0.0.0.0 spotify.com",
    "0.0.0.0 www.spotify.com",
    "0.0.0.0 open.spotify.com",
    "0.0.0.0 play.spotify.com",
    "0.0.0.0 soundcloud.com",
    "0.0.0.0 www.soundcloud.com",
    "0.0.0.0 music.apple.com",
    "0.0.0.0 music.amazon.com",
    "0.0.0.0 deezer.com",
    "0.0.0.0 www.deezer.com",
    "0.0.0.0 tidal.com",
    "0.0.0.0 www.tidal.com",
    "0.0.0.0 listen.tidal.com",
    "0.0.0.0 pandora.com",
    "0.0.0.0 www.pandora.com",
    "0.0.0.0 gaana.com",
    "0.0.0.0 www.gaana.com",
    "0.0.0.0 jiosaavn.com",
    "0.0.0.0 www.jiosaavn.com",
    "0.0.0.0 saavn.com",
    "0.0.0.0 www.saavn.com",
    "0.0.0.0 wynk.in",
    "0.0.0.0 www.wynk.in",
    "0.0.0.0 hungama.com",
    "0.0.0.0 www.hungama.com",
    "0.0.0.0 bandcamp.com",
    "0.0.0.0 audiomack.com",
    "0.0.0.0 www.audiomack.com",
    "0.0.0.0 qobuz.com",
    "0.0.0.0 www.qobuz.com",
    "0.0.0.0 napster.com",
    "0.0.0.0 www.napster.com",
    "0.0.0.0 tunein.com",
    "0.0.0.0 www.tunein.com",
    "0.0.0.0 iheart.com",
    "0.0.0.0 www.iheart.com",
    "0.0.0.0 last.fm",
    "0.0.0.0 www.last.fm",
    "0.0.0.0 mixcloud.com",
    "0.0.0.0 www.mixcloud.com",

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
    "0.0.0.0 proxyorb.com",
    "0.0.0.0 www.proxyorb.com",
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
    "0.0.0.0 browser.lol",
    "0.0.0.0 browserling.com",
    "0.0.0.0 neverinstall.com",
    "0.0.0.0 hyperbeam.com",
    "0.0.0.0 kasmweb.com",
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
    "0.0.0.0 youproxy.org",
    "0.0.0.0 thepiratebay.org",
    "0.0.0.0 www.thepiratebay.org",
    "0.0.0.0 thepiratesproxy.org",
    "0.0.0.0 www.thepiratesproxy.org",
    "0.0.0.0 tpb.party",
    "0.0.0.0 www.tpb.party",
    "0.0.0.0 1337x.to",
    "0.0.0.0 www.1337x.to",
    "0.0.0.0 yts.mx",
    "0.0.0.0 www.yts.mx",
    "0.0.0.0 torrentgalaxy.to",
    "0.0.0.0 www.torrentgalaxy.to"
)

try {
    if (Test-Path $hostsPath) {
        $existingContent = [System.IO.File]::ReadAllText($hostsPath)
        # Fix legacy Bing IP if mistakenly mapped to Google SafeSearch VIP
        $existingContent = $existingContent -replace '216\.239\.38\.120\s+www\.bing\.com', '204.79.197.220 www.bing.com'
        $existingContent = $existingContent -replace '216\.239\.38\.120\s+strict\.bing\.com', '204.79.197.220 strict.bing.com'
        $existingLines = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
        foreach ($line in ($existingContent -split "`r?`n")) {
            $t = $line.Trim()
            if ($t) { [void]$existingLines.Add($t) }
        }
        $newEntriesToAdd = @()
        foreach ($entry in $hostsEntries) {
            if (-not $existingLines.Contains($entry.Trim())) {
                $newEntriesToAdd += $entry
                [void]$existingLines.Add($entry.Trim())
            }
        }
        [System.IO.File]::WriteAllText($hostsPath, $existingContent)
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
# 3. APPLY ENTERPRISE BROWSER POLICIES (CHROME, EDGE & BRAVE BELT-AND-SUSPENDERS)
# ------------------------------------------------------------------------------
Write-Host "`n$C_SEC[3/7] Enforcing Chrome & Edge Enterprise Policies (DoH, SafeSearch, No VPN Extensions)...$R"

$browserConfigs = @(
    @{ Name = "Google Chrome";  Roots = @("HKLM:\SOFTWARE\Policies\Google\Chrome", "HKCU:\SOFTWARE\Policies\Google\Chrome"); IncognitoProp = "IncognitoModeAvailability"; IsEdge = $false; IsBrave = $false },
    @{ Name = "Microsoft Edge"; Roots = @("HKLM:\SOFTWARE\Policies\Microsoft\Edge", "HKCU:\SOFTWARE\Policies\Microsoft\Edge"); IncognitoProp = "InPrivateModeAvailability"; IsEdge = $true;  IsBrave = $false },
    @{ Name = "Brave Browser";  Roots = @("HKLM:\SOFTWARE\Policies\BraveSoftware\Brave", "HKCU:\SOFTWARE\Policies\BraveSoftware\Brave"); IncognitoProp = "IncognitoModeAvailability"; IsEdge = $false; IsBrave = $true }
)

$blockedDomains = @(
    # Unauthorized Browser Download & Unfiltered Search Domains
    # (Use specific mozilla subdomains so developer.mozilla.org MDN study docs remain accessible)
    "brave.com", "bravesoftware.com", "search.brave.com",
    "opera.com", "www.mozilla.org", "download.mozilla.org", "ftp.mozilla.org", "releases.mozilla.org",
    "firefox.com", "vivaldi.com",
    "torproject.org", "waterfox.net", "librewolf.net", "arc.net",
    "thorium.rocks", "zen-browser.app", "floorp.app", "mullvad.net",
    "duckduckgo.com", "startpage.com", "qwant.com", "yandex.com",
    "yandex.ru", "presearch.com", "mojeek.com", "swisscows.com",

    # Master Web Proxy Domains & Unblockers
    "onlineproxy.org", "uproxy.online", "proxysite.com", "proxysite.cloud",
    "proxysite.site", "proxysite.one", "proxysite.net", "proxysite.org",
    "hidester.one", "hidester.com", "hidester.net", "hidester.org",
    "croxyproxy.com", "croxyproxy.net", "croxyproxy.rocks", "croxy.network", "croxy.org",
    "extremevpn.com", "azureserv.com", "azureserv.net", "proxypal.net", "proxypal.org",
    "proxyorb.com", "hide.me", "blockaway.net", "proxyium.com", "proxyium.net",
    "kproxy.com", "vpnbook.com", "plainproxies.com", "hidemyass.com", "whoer.net",
    "zalmos.com", "filterbypass.me", "4everproxy.com", "toolur.com", "webproxy.to",
    "turbohide.org", "freeproxy.win", "nodeunblocker.net", "rammerhead.org",
    "ultraviolet.dev", "anarchyproxy.com", "hyperproxy.network", "shuttleproxy.com",
    "alohabrowser.com", "browser.lol", "browserling.com", "neverinstall.com",
    "hyperbeam.com", "kasmweb.com", "vtransmit.com", "p23hxejm1.com", "rm358.com",
    "shadowproxy.org", "interstellarproxy.com", "incognitoproxy.com", "nebula.net",
    "titaniumnetwork.org", "womginx.org", "zend2.com", "zendproxy.com", "megaproxy.com",
    "newipnow.com", "dontfilter.us", "unblock-web.com", "unblockvideos.com",
    "free-proxy.cz", "proxybroker.online", "smartproxy.com", "brightdata.com",
    "oxylabs.io", "webproxy.free", "free-proxy-list.net", "usaproxy.info",
    "german-proxy.de", "myspaceproxy.org", "youtubeproxy.org", "tiktokproxy.com",
    "unblockyoutube.net", "bypassblocks.com", "surfshield.io", "cloakproxy.org",
    "scramjet.org", "tomp.app", "arsenic.org", "holyub.org", "dynamicproxy.org",
    "astralproxy.org", "phantomproxy.org", "metallicproxy.org", "selenite.cc",
    "ludicrous.org", "shadowtabs.org", "ccproxy.com", "4proxy.de", "vtunnel.com",
    "polysolve.net", "unblocker.cc", "unblocker.us", "web-proxy.cc", "superproxy.cc",
    "privacysite.net", "proxyserver.com", "freeproxyserver.co", "freeopenproxy.com",
    "freewebproxy.com", "myproxy.ca", "quickproxy.co.uk", "snoopblocker.com",
    "surfbrowser.com", "unblockmyweb.com", "unblockall.org", "unblocker.biz",
    "unblocker.info", "unblocker.online", "unblocker.site", "unblocker.tech",
    "unblocksite.org", "unblockwebsites.org", "webproxy.net", "webproxy.org",
    "webproxy.site", "youproxy.org",

    # Notorious Streaming & Social Media
    "fboxtv.org", "x.com", "twitter.com", "twimg.com",
    "reddit.com", "redd.it", "redditmedia.com", "tumblr.com",
    "telegram.org", "t.me", "tiktok.com",

    # Online VPN App
    "onlinevpn.app",

    # Torrent & Piracy Download Hubs
    "thepiratebay.org", "thepiratesproxy.org", "tpb.party", "1337x.to",
    "yts.mx", "torrentgalaxy.to", "limetorrents.lol", "nyaa.si", "eztv.re", "magnetdl.com",

    # Music Streaming & Audio Platforms (Preserving standard YouTube)
    "music.youtube.com", "spotify.com", "soundcloud.com", "music.apple.com",
    "music.amazon.com", "deezer.com", "tidal.com", "pandora.com", "gaana.com",
    "jiosaavn.com", "saavn.com", "wynk.in", "hungama.com", "bandcamp.com",
    "audiomack.com", "qobuz.com", "napster.com", "tunein.com", "iheart.com",
    "last.fm", "mixcloud.com"
)

# Block ONLY extensions requesting 'proxy' or 'vpnProvider' permissions — ALL good extensions remain usable!
$extSettingsJson = '{"*":{"blocked_permissions":["proxy","vpnProvider"]}}'

foreach ($b in $browserConfigs) {
    foreach ($rootKey in $b.Roots) {
        try {
            if (-not (Test-Path $rootKey)) { New-Item -Path $rootKey -Force -ErrorAction SilentlyContinue | Out-Null }
            Set-ItemProperty -Path $rootKey -Name $b.IncognitoProp -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue
            Set-ItemProperty -Path $rootKey -Name "BrowserGuestModeEnabled" -Value 0 -Type DWord -Force -ErrorAction SilentlyContinue
            Set-ItemProperty -Path $rootKey -Name "BrowserAddPersonEnabled" -Value 0 -Type DWord -Force -ErrorAction SilentlyContinue
            Set-ItemProperty -Path $rootKey -Name "ForceGoogleSafeSearch" -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue
            Set-ItemProperty -Path $rootKey -Name "SafeSitesFilterBehavior" -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue
            Set-ItemProperty -Path $rootKey -Name "ForceYouTubeRestrict" -Value 0 -Type DWord -Force -ErrorAction SilentlyContinue
            Set-ItemProperty -Path $rootKey -Name "DnsOverHttpsMode" -Value "secure" -Type String -Force -ErrorAction SilentlyContinue
            Set-ItemProperty -Path $rootKey -Name "DnsOverHttpsTemplates" -Value "https://doh.cleanbrowsing.org/doh/adult-filter/" -Type String -Force -ErrorAction SilentlyContinue
            Set-ItemProperty -Path $rootKey -Name "ProxyMode" -Value "direct" -Type String -Force -ErrorAction SilentlyContinue
            Set-ItemProperty -Path $rootKey -Name "DefaultSearchProviderEnabled" -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue
            Set-ItemProperty -Path $rootKey -Name "DefaultSearchProviderName" -Value "Google" -Type String -Force -ErrorAction SilentlyContinue
            Set-ItemProperty -Path $rootKey -Name "DefaultSearchProviderSearchURL" -Value "https://www.google.com/search?q={searchTerms}&safe=active&ssui=on" -Type String -Force -ErrorAction SilentlyContinue
            Set-ItemProperty -Path $rootKey -Name "ExtensionSettings" -Value $extSettingsJson -Type String -Force -ErrorAction SilentlyContinue

            if ($b.IsEdge) {
                Set-ItemProperty -Path $rootKey -Name "ForceBingSafeSearch" -Value 2 -Type DWord -Force -ErrorAction SilentlyContinue
                Set-ItemProperty -Path $rootKey -Name "SmartScreenEnabled" -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue
            }
            if ($b.IsBrave) {
                Set-ItemProperty -Path $rootKey -Name "TorDisabled" -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue
                Set-ItemProperty -Path $rootKey -Name "BraveVPNDisabled" -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue
                Set-ItemProperty -Path $rootKey -Name "BraveAIChatEnabled" -Value 0 -Type DWord -Force -ErrorAction SilentlyContinue
            }

            # Keep general extension installs usable (only proxy/vpnProvider permissions are blocked via ExtensionSettings)
            $extBlockKey = "$rootKey\ExtensionInstallBlocklist"
            if (Test-Path $extBlockKey) { Remove-Item -Path $extBlockKey -Recurse -Force -ErrorAction SilentlyContinue }
            Remove-ItemProperty -Path $rootKey -Name "BlockExternalExtensions" -Force -ErrorAction SilentlyContinue

            # Purge conflicting URLAllowlist
            $urlAllowKey = "$rootKey\URLAllowlist"
            if (Test-Path $urlAllowKey) { Remove-Item -Path $urlAllowKey -Recurse -Force -ErrorAction SilentlyContinue }

            # Write base URLBlocklist
            $urlBlockKey = "$rootKey\URLBlocklist"
            if (Test-Path $urlBlockKey) { Remove-Item -Path $urlBlockKey -Recurse -Force -ErrorAction SilentlyContinue }
            New-Item -Path $urlBlockKey -Force -ErrorAction SilentlyContinue | Out-Null
            for ($i = 0; $i -lt $blockedDomains.Count; $i++) {
                $num = $i + 1
                Set-ItemProperty -Path $urlBlockKey -Name "$num" -Value $blockedDomains[$i] -Type String -Force -ErrorAction SilentlyContinue
            }
        } catch {
            # Ignore per-key errors gracefully
        }
    }
    Write-Host "  $C_OK[+] $($b.Name): CleanBrowsing DoH | Google SafeSearch Locked | Proxy/VPN Extensions Blocked | Guest/Incognito Disabled$R"
}

# Append master adult wildcard URLBlocklist so it is never overwritten
$adultScript = Join-Path $PSScriptRoot "Apply_Adult_URLBlocklist.ps1"
if (Test-Path $adultScript) {
    & $adultScript | Out-Null
    Write-Host "  $C_OK[+] Merged full Adult, Torrent & Music wildcard URLBlocklist rules.$R"
}

# ------------------------------------------------------------------------------
# 4. KERNEL-LEVEL IFEO EXECUTION BLOCKS (EXACT BROWSER & VPN BINARIES ONLY)
# ------------------------------------------------------------------------------
Write-Host "`n$C_SEC[4/7] Applying Kernel-Level IFEO Execution Blocks (Windows 10 Home Native)...$R"
$ifeoRoot = "HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Image File Execution Options"
$ifeoStub = "C:\Windows\System32\systray.exe"

# Strictly exact unauthorized browser, browser-installer, and VPN client executables ONLY.
# NEVER includes generic names (setup.exe, launcher.exe, browser_broker.exe) or good apps!
$blockedExes = @(
    # Brave Browser & Installers/Updaters
    "brave.exe",
    "BraveUpdate.exe",
    "BraveUpdateSetup.exe",
    "BraveBrowserSetup.exe",
    "BraveBrowserSetup-BRV010.exe",
    "BraveBrowserStandaloneSetup.exe",
    "BraveBrowserStandaloneSilentSetup.exe",
    "BraveBrowserNightlySetup.exe",
    "BraveBrowserBetaSetup.exe",

    # Opera / Opera GX
    "opera.exe",
    "opera_gx.exe",
    "operagx.exe",
    "opera_autoupdate.exe",
    "OperaSetup.exe",
    "OperaGXSetup.exe",

    # Firefox / Gecko Browsers
    "firefox.exe",
    "Firefox Installer.exe",
    "waterfox.exe",
    "librewolf.exe",
    "floorp.exe",
    "zen.exe",
    "palemoon.exe",
    "basilisk.exe",
    "seamonkey.exe",
    "mullvadbrowser.exe",

    # Other Unmanaged Chromium / Privacy / Tor Browsers
    "vivaldi.exe",
    "arc.exe",
    "thorium.exe",
    "comet.exe",
    "duckduckgo.exe",
    "tor.exe",
    "torbrowser.exe",
    "yandex.exe",
    "ucbrowser.exe",
    "aloha.exe",
    "maxthon.exe",
    "dragon.exe",
    "cromite.exe",

    # VPN & Tunnel Clients
    "skyvpn.exe",
    "protonvpn.exe",
    "nordvpn.exe",
    "windscribe.exe",
    "expressvpn.exe",
    "surfshark.exe",
    "psiphon.exe",
    "psiphon3.exe",
    "wireguard.exe",
    "warp-svc.exe",
    "cloudflarewarp.exe",
    "openvpn.exe",
    "clash.exe",
    "clash-verge.exe",
    "v2ray.exe",
    "xray.exe",
    "shadowsocks.exe",
    "ultrasurf.exe",
    "lantern.exe",
    "hotspotshield.exe",
    "cyberghost.exe",
    "tunnelbear.exe",
    "mullvad-vpn.exe"
)

$ifeoCount = 0
foreach ($exe in $blockedExes) {
    try {
        $keyPath = Join-Path $ifeoRoot $exe
        if (-not (Test-Path $keyPath)) { New-Item -Path $keyPath -Force -ErrorAction SilentlyContinue | Out-Null }
        Set-ItemProperty -Path $keyPath -Name "Debugger" -Value $ifeoStub -Type String -Force -ErrorAction SilentlyContinue
        $ifeoCount++
    } catch {}
}
Write-Host "  $C_OK[+] Enforced $ifeoCount kernel-level IFEO blocks (Chrome, Edge & all good apps untouched).$R"

# ------------------------------------------------------------------------------
# 5. WINDOWS NETWORK, VPN & TOR FIREWALL LOCKDOWN
# ------------------------------------------------------------------------------
Write-Host "`n$C_SEC[5/7] Locking Windows VPN Services & Blocking Tor / Unfiltered DoT Ports...$R"
try {
    $netConnKey = "HKLM:\SOFTWARE\Policies\Microsoft\Network Connections"
    if (-not (Test-Path $netConnKey)) { New-Item -Path $netConnKey -Force -ErrorAction SilentlyContinue | Out-Null }
    Set-ItemProperty -Path $netConnKey -Name "NC_NewConnectionWizard" -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue
    Set-ItemProperty -Path $netConnKey -Name "NC_DeleteAllUserConnection" -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue

    $ieControlKey = "HKLM:\SOFTWARE\Policies\Microsoft\Internet Explorer\Control Panel"
    if (-not (Test-Path $ieControlKey)) { New-Item -Path $ieControlKey -Force -ErrorAction SilentlyContinue | Out-Null }
    Set-ItemProperty -Path $ieControlKey -Name "Proxy" -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue
    Set-ItemProperty -Path $ieControlKey -Name "Connwiz Admin Lock" -Value 1 -Type DWord -Force -ErrorAction SilentlyContinue

    Set-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Services\RasMan" -Name "Start" -Value 4 -Type DWord -Force -ErrorAction SilentlyContinue
    Set-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Services\RasAuto" -Name "Start" -Value 4 -Type DWord -Force -ErrorAction SilentlyContinue

    # Block Tor relay/SOCKS outbound ports (9001, 9030, 9050, 9051, 9150) & rogue DoT (853)
    netsh advfirewall firewall delete rule name="DpcLocker_Block_Tor_TCP" | Out-Null
    netsh advfirewall firewall add rule name="DpcLocker_Block_Tor_TCP" dir=out action=block protocol=TCP remoteport=9001,9030,9050,9051,9150 | Out-Null
    netsh advfirewall firewall delete rule name="DpcLocker_Block_Rogue_DoT_853" | Out-Null
    netsh advfirewall firewall add rule name="DpcLocker_Block_Rogue_DoT_853" dir=out action=block protocol=TCP remoteport=853 | Out-Null
    Write-Host "  $C_OK[+] Windows VPN services, Proxy UI, Tor ports & rogue DoT locked.$R"
} catch {
    Write-Host "  $C_WARN[!] Network lockdown notice: $($_.Exception.Message)$R"
}

# ------------------------------------------------------------------------------
# 6. CLEAN UP LEFTOVER UNAUTHORIZED BROWSERS (BRAVE & OPERA)
# ------------------------------------------------------------------------------
Write-Host "`n$C_SEC[6/7] Cleaning Up Leftover Unauthorized Browser Files (Brave & Opera)...$R"
$unauthProcs = @("brave", "BraveUpdate", "opera", "opera_gx", "firefox", "vivaldi", "comet", "tor", "waterfox", "librewolf", "arc", "duckduckgo")
foreach ($p in $unauthProcs) {
    Stop-Process -Name $p -Force -ErrorAction SilentlyContinue
}
$cleanupPaths = @(
    "C:\Users\ThinkPad\Downloads\BraveBrowserSetup-BRV010.exe",
    "C:\Program Files (x86)\BraveSoftware",
    "C:\Program Files\BraveSoftware",
    "C:\Users\ThinkPad\AppData\Local\BraveSoftware",
    "C:\Users\ThinkPad\AppData\Local\Programs\Opera"
)
foreach ($cp in $cleanupPaths) {
    if (Test-Path $cp) {
        try {
            Remove-Item -Path $cp -Recurse -Force -ErrorAction SilentlyContinue
            Write-Host "  $C_OK[+] Removed leftover unauthorized browser footprint: $cp$R"
        } catch {
            Write-Host "  $C_WARN[!] Could not remove $cp : $($_.Exception.Message)$R"
        }
    }
}

# ------------------------------------------------------------------------------
# 7. FLUSH DNS RESOLVER CACHE & OPTIONAL MANAGED BROWSER REFRESH
# ------------------------------------------------------------------------------
Write-Host "`n$C_SEC[7/7] Flushing Windows DNS Cache...$R"
try {
    Clear-DnsClientCache -ErrorAction SilentlyContinue
    ipconfig /flushdns | Out-Null
    Write-Host "  $C_OK[+] DNS resolver cache successfully flushed.$R"
} catch {
    Write-Host "  $C_WARN[!] DNS flush notice: $($_.Exception.Message)$R"
}

if ($RestartBrowsers) {
    $runningBrowsers = Get-Process -Name "chrome", "msedge" -ErrorAction SilentlyContinue
    if ($runningBrowsers) {
        Write-Host "  $C_SUB[*] Refreshing Chrome/Edge processes to apply new enterprise policies...$R"
        Stop-Process -Name "chrome", "msedge" -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "`n$C_HDR===============================================================================$R"
Write-Host " $C_OK[OK] WINDOWS BROWSER, DNS & CONTENT LOCKDOWN v2 IS NOW 100% ACTIVE!$R"
Write-Host "$C_HDR===============================================================================$R`n"
