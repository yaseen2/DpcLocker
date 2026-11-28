# ==============================================================================
# DPCLOCKER :: ENFORCE ADULT WILDCARD URLBLOCKLIST IN EDGE & CHROME
# ==============================================================================
# Injects major adult wildcard patterns (*xhamster*, *pornhub*, *xvideos*, etc.)
# into HKLM\SOFTWARE\Policies\Microsoft\Edge\URLBlocklist and
# HKLM\SOFTWARE\Policies\Google\Chrome\URLBlocklist.
# Also configures Edge Strict SafeSearch and Anti-Bypass policies.
# ==============================================================================

$AdultPatterns = @(
    # Major Tube & Video Hubs
    "*xhamster*", "*xhopen*", "*xhlink*", "*pornhub*", "*xvideos*", "*xnxx*",
    "*redtube*", "*youporn*", "*spankbang*", "*tube8*", "*beeg.com*", "*tnaflix*",
    "*drtuber*", "*eporner*", "*hqporner*", "*thumbzilla*", "*porn555*", "*porn300*",
    "*pornbox*", "*porntube*", "*porndoe*", "*pornmd*", "*fuq.com*", "*nudevista*",
    "*tubegalore*", "*sublimeporno*", "*xcafe*", "*ixxx.com*", "*txxx.com*", "*pornhat*",
    "*vxxx.com*", "*xbabe.com*", "*freeomovie*", "*faphouse*", "*daftsex*", "*heavy-r*",
    "*motherless.com*", "*erome.com*", "*pornpics.com*", "*imagefap.com*",

    # Premium Studios & Paysites
    "*brazzers*", "*naughtyamerica*", "*realitykings*", "*bangbros*", "*evilangel*",
    "*digitalplayground*", "*mofos.com*", "*twistys*", "*kink.com*", "*adulttime*",
    "*sweetheartvideo*", "*babes.com*", "*puremature*",

    # Live Cam & Adult Chat Networks
    "*chaturbate*", "*stripchat*", "*camsoda*", "*bongacams*", "*livejasmin*",
    "*cam4.com*", "*myfreecams*", "*flirt4free*", "*camwhores*", "*imlive*",
    "*streamate*", "*adultfriendfinder*", "*cams.com*",

    # Creator & Paywall Adult Platforms
    "*onlyfans.com*", "*fansly.com*", "*loyalfans.com*", "*manyvids.com*",
    "*clips4sale*", "*modelhub.com*",

    # Hentai & Anime Adult Platforms
    "*nhentai*", "*hentaihaven*", "*hanime.tv*", "*e-hentai.org*", "*hitomi.la*",
    "*tsumino.com*", "*fakku.net*", "*luscious.net*", "*hentai2read*",
    "*rule34.xxx*", "*rule34.paheal*", "*gelbooru.com*", "*tbib.org*",

    # Asian / JAV Networks
    "*javlibrary*", "*javbus*", "*jable.tv*", "*missav*", "*7mmtv*",
    "*avgle*", "*javhd*", "*supjav*", "*netflav*", "*onejav*",

    # Adult TLDs
    "*.xxx", "*.porn", "*.adult", "*.sex"
)

$MusicAndVpnPatterns = @(
    # Online VPN App
    "*onlinevpn.app*", "onlinevpn.app",

    # Music Streaming & Audio Platforms (Preserving standard YouTube)
    "music.youtube.com", "*music.youtube.com*",
    "spotify.com", "*spotify.com*", "open.spotify.com", "*open.spotify.com*",
    "soundcloud.com", "*soundcloud.com*",
    "music.apple.com", "*music.apple.com*",
    "music.amazon.com", "*music.amazon.*",
    "deezer.com", "*deezer.com*",
    "tidal.com", "*tidal.com*",
    "pandora.com", "*pandora.com*",
    "gaana.com", "*gaana.com*",
    "jiosaavn.com", "*jiosaavn.com*", "saavn.com", "*saavn.com*",
    "wynk.in", "*wynk.in*",
    "hungama.com", "*hungama.com*",
    "bandcamp.com", "*bandcamp.com*",
    "audiomack.com", "*audiomack.com*",
    "qobuz.com", "*qobuz.com*",
    "napster.com", "*napster.com*",
    "tunein.com", "*tunein.com*",
    "iheart.com", "*iheart.com*",
    "last.fm", "*last.fm*",
    "mixcloud.com", "*mixcloud.com*"
)

$TorrentAndPiracyPatterns = @(
    # The Pirate Bay & Torrent Indexers
    "thepiratebay.org", "*thepiratebay*", "*thepiratebay.org*", "*piratebay*",
    "thepiratesproxy.org", "*thepiratesproxy*", "*thepiratesproxy.org*",
    "pirateproxy.*", "*pirateproxy*", "*piratesproxy*",
    "tpb.party", "*tpb.party*",
    "1337x.to", "*1337x.*",
    "yts.mx", "*yts.*",
    "torrentgalaxy.to", "*torrentgalaxy*",
    "limetorrents.*", "*limetorrents*",
    "nyaa.si", "*nyaa.si*",
    "eztv.re", "magnetdl.com"
)

$UnauthorizedBrowserPatterns = @(
    "brave.com", "bravesoftware.com", "search.brave.com",
    "opera.com", "www.mozilla.org", "download.mozilla.org", "ftp.mozilla.org", "releases.mozilla.org",
    "firefox.com", "vivaldi.com", "torproject.org",
    "waterfox.net", "librewolf.net", "arc.net", "thorium.rocks",
    "zen-browser.app", "floorp.app", "mullvad.net",
    "duckduckgo.com", "startpage.com", "qwant.com", "yandex.com", "yandex.ru"
)

$AllPatternsToBlock = $AdultPatterns + $MusicAndVpnPatterns + $TorrentAndPiracyPatterns + $UnauthorizedBrowserPatterns

$Targets = @(
    "HKLM:\SOFTWARE\Policies\Microsoft\Edge",
    "HKLM:\SOFTWARE\Policies\Google\Chrome",
    "HKLM:\SOFTWARE\Policies\BraveSoftware\Brave"
)

foreach ($target in $Targets) {
    $browserName = Split-Path $target -Leaf
    Write-Host "[*] Processing $browserName URLBlocklist..." -ForegroundColor Cyan
    
    $blockListPath = Join-Path $target "URLBlocklist"
    if (-not (Test-Path $blockListPath)) {
        New-Item -Path $blockListPath -Force | Out-Null
    }

    # Collect existing patterns
    $existingProps = Get-ItemProperty -Path $blockListPath -ErrorAction SilentlyContinue
    $existingValues = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    $maxIndex = 0

    if ($existingProps) {
        foreach ($prop in $existingProps.PSObject.Properties) {
            if ($prop.Name -match '^\d+$') {
                $idx = [int]$prop.Name
                if ($idx -gt $maxIndex) { $maxIndex = $idx }
                if ($prop.Value) {
                    [void]$existingValues.Add($prop.Value.ToString().Trim())
                }
            }
        }
    }

    # Add missing adult, VPN, and music patterns
    $addedCount = 0
    foreach ($pattern in $AllPatternsToBlock) {
        $p = $pattern.Trim()
        if (-not $existingValues.Contains($p)) {
            $maxIndex++
            New-ItemProperty -Path $blockListPath -Name "$maxIndex" -Value $p -PropertyType String -Force | Out-Null
            [void]$existingValues.Add($p)
            $addedCount++
        }
    }

    Write-Host " [+] $browserName`: Added $addedCount new wildcard patterns (Total items: $maxIndex)" -ForegroundColor Green
}

# Apply Chrome, Edge & Brave Core Security Policies (CleanBrowsing Adult DoH, SafeSearch, No Proxy/VPN Extensions)
Write-Host "[*] Hardening Chrome, Edge & Brave Enterprise Policies..." -ForegroundColor Cyan
$extSettingsJson = '{"*":{"blocked_permissions":["proxy","vpnProvider"]}}'
$dohTemplate = "https://doh.cleanbrowsing.org/doh/adult-filter/"
$googleSafeSearchUrl = "https://www.google.com/search?q={searchTerms}&safe=active&ssui=on"

foreach ($policyPath in $Targets) {
    if (-not (Test-Path $policyPath)) { New-Item -Path $policyPath -Force | Out-Null }
    New-ItemProperty -Path $policyPath -Name "ForceGoogleSafeSearch" -Value 1 -PropertyType DWord -Force | Out-Null
    New-ItemProperty -Path $policyPath -Name "SafeSitesFilterBehavior" -Value 1 -PropertyType DWord -Force | Out-Null
    New-ItemProperty -Path $policyPath -Name "ForceYouTubeRestrict" -Value 0 -PropertyType DWord -Force | Out-Null
    New-ItemProperty -Path $policyPath -Name "BrowserGuestModeEnabled" -Value 0 -PropertyType DWord -Force | Out-Null
    New-ItemProperty -Path $policyPath -Name "BrowserAddPersonEnabled" -Value 0 -PropertyType DWord -Force | Out-Null
    New-ItemProperty -Path $policyPath -Name "DnsOverHttpsMode" -Value "secure" -PropertyType String -Force | Out-Null
    New-ItemProperty -Path $policyPath -Name "DnsOverHttpsTemplates" -Value $dohTemplate -PropertyType String -Force | Out-Null
    New-ItemProperty -Path $policyPath -Name "ProxyMode" -Value "direct" -PropertyType String -Force | Out-Null
    New-ItemProperty -Path $policyPath -Name "DefaultSearchProviderEnabled" -Value 1 -PropertyType DWord -Force | Out-Null
    New-ItemProperty -Path $policyPath -Name "DefaultSearchProviderName" -Value "Google" -PropertyType String -Force | Out-Null
    New-ItemProperty -Path $policyPath -Name "DefaultSearchProviderSearchURL" -Value $googleSafeSearchUrl -PropertyType String -Force | Out-Null
    New-ItemProperty -Path $policyPath -Name "ExtensionSettings" -Value $extSettingsJson -PropertyType String -Force | Out-Null

    if ($policyPath -like "*Microsoft\Edge") {
        New-ItemProperty -Path $policyPath -Name "InPrivateModeAvailability" -Value 1 -PropertyType DWord -Force | Out-Null
        New-ItemProperty -Path $policyPath -Name "ForceBingSafeSearch" -Value 2 -PropertyType DWord -Force | Out-Null
        New-ItemProperty -Path $policyPath -Name "SmartScreenEnabled" -Value 1 -PropertyType DWord -Force | Out-Null
    } else {
        New-ItemProperty -Path $policyPath -Name "IncognitoModeAvailability" -Value 1 -PropertyType DWord -Force | Out-Null
    }

    if ($policyPath -like "*BraveSoftware\Brave") {
        New-ItemProperty -Path $policyPath -Name "TorDisabled" -Value 1 -PropertyType DWord -Force | Out-Null
        New-ItemProperty -Path $policyPath -Name "BraveVPNDisabled" -Value 1 -PropertyType DWord -Force | Out-Null
        New-ItemProperty -Path $policyPath -Name "BraveAIChatEnabled" -Value 0 -PropertyType DWord -Force | Out-Null
    }
}

Write-Host "[+] Chrome, Edge & Brave Hardening Complete: CleanBrowsing Adult DoH + SafeSearch + No VPN Extensions + URLBlocklist enforced." -ForegroundColor Green
