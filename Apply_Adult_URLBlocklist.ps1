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

$Targets = @(
    "HKLM:\SOFTWARE\Policies\Microsoft\Edge",
    "HKLM:\SOFTWARE\Policies\Google\Chrome"
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

    # Add missing adult patterns
    $addedCount = 0
    foreach ($pattern in $AdultPatterns) {
        $p = $pattern.Trim()
        if (-not $existingValues.Contains($p)) {
            $maxIndex++
            New-ItemProperty -Path $blockListPath -Name "$maxIndex" -Value $p -PropertyType String -Force | Out-Null
            [void]$existingValues.Add($p)
            $addedCount++
        }
    }

    Write-Host " [+] $browserName`: Added $addedCount new adult wildcard patterns (Total items: $maxIndex)" -ForegroundColor Green
}

# Apply Edge-Specific Policies (Strict SafeSearch, DoH disabled, SmartScreen enabled)
Write-Host "[*] Hardening Microsoft Edge Security & SafeSearch Policies..." -ForegroundColor Cyan
$edgePolicyPath = "HKLM:\SOFTWARE\Policies\Microsoft\Edge"

# Strict SafeSearch on Bing (2 = Strict) and Google (1 = Enabled)
New-ItemProperty -Path $edgePolicyPath -Name "ForceBingSafeSearch" -Value 2 -PropertyType DWord -Force | Out-Null
New-ItemProperty -Path $edgePolicyPath -Name "ForceGoogleSafeSearch" -Value 1 -PropertyType DWord -Force | Out-Null

# Disable InPrivate / Incognito (1 = InPrivate disabled)
New-ItemProperty -Path $edgePolicyPath -Name "InPrivateModeAvailability" -Value 1 -PropertyType DWord -Force | Out-Null

# Disable Edge Secure DNS (DoH) so Edge cannot bypass CleanBrowsing DNS
New-ItemProperty -Path $edgePolicyPath -Name "DnsOverHttpsMode" -Value "off" -PropertyType String -Force | Out-Null

# Enable SmartScreen
New-ItemProperty -Path $edgePolicyPath -Name "SmartScreenEnabled" -Value 1 -PropertyType DWord -Force | Out-Null
New-ItemProperty -Path $edgePolicyPath -Name "SmartScreenPuaEnabled" -Value 1 -PropertyType DWord -Force | Out-Null

Write-Host "[+] Microsoft Edge Hardening Complete: Strict SafeSearch + URLBlocklist enforced." -ForegroundColor Green
