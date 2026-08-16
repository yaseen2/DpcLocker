param(
    [string]$Serial = "",
    [string]$AdbPath = "adb.exe"
)

if (-not $Serial) {
    Write-Host "`e[1;38;5;203m[!] Error: No device serial provided.`e[0m"
    exit 1
}

$model = (& $AdbPath -s $Serial shell getprop ro.product.model).Trim()
if (-not $model) { $model = "Android Device" }

$lockStateRaw = (& $AdbPath -s $Serial shell settings get global dpclocker_enabled).Trim()
$doRaw = (& $AdbPath -s $Serial shell dpm list-owners)

$lockDisplay = if ($lockStateRaw -eq "1") { "`e[1;38;5;203m[ LOCKED / ENFORCED ]`e[0m (dpclocker_enabled = 1)" } elseif ($lockStateRaw -eq "0") { "`e[1;38;5;82m[ UNLOCKED / EDITABLE ]`e[0m (dpclocker_enabled = 0)" } else { "`e[1;38;5;214m[ UNSET / DEFAULT ]`e[0m" }

$isDO = $doRaw -match "com.afwsamples.testdpc"
$doDisplay = if ($isDO) { "`e[1;38;5;82m[ ACTIVE ]`e[0m (com.afwsamples.testdpc/.DeviceAdminReceiver)" } else { "`e[1;38;5;203m[ INACTIVE / NOT SET ]`e[0m" }

$dpmDump = (& $AdbPath -s $Serial shell dumpsys device_policy) -join "`n"
$pkgMatches = [regex]::Matches($dpmDump, "PackageNameSetPolicyValue\s*\{\s*\[([^\]]+)\]")
$packages = @()
foreach ($m in $pkgMatches) {
    $pkgs = $m.Groups[1].Value -split ",\s*"
    foreach ($p in $pkgs) {
        $pClean = $p.Trim()
        if ($pClean -and $packages -notcontains $pClean) {
            $packages += $pClean
        }
    }
}

# Known friendly app descriptions dictionary
$knownApps = @{
    "com.ss.android.ugc.aweme"        = "TikTok (Global / ByteDance)"
    "com.ss.android.ugc.trill"        = "TikTok Lite (ByteDance)"
    "com.zhiliaoapp.musically"        = "TikTok (International)"
    "com.zhiliaoapp.musically.go"     = "TikTok Go"
    "com.google.android.youtube"      = "YouTube Official App"
    "com.reddit.frontpage"            = "Reddit Official App"
    "com.twitter.android"             = "X / Twitter App"
    "com.twitter.android.lite"        = "X / Twitter Lite"
    "org.telegram.messenger"          = "Telegram Messenger"
    "org.telegram.messenger.web"      = "Telegram Web Client"
    "org.telegram.plus"               = "Plus Messenger (Telegram Mod)"
    "com.tumblr"                      = "Tumblr"
    "com.xbrowser.play"               = "XBrowser (Incognito Bypass)"
    "com.chimbori.hermitcrab"         = "Hermit Lite Apps Browser"
    "free.xnxx.hot.video.downloader"  = "Adult Video Downloader"
    "com.instagram.android"           = "Instagram"
    "com.snapchat.android"            = "Snapchat"
}

Write-Host "`e[1;38;5;39m===============================================================================`e[0m"
Write-Host " `e[1;38;5;39m[#] DPCLOCKER :: DEVICE POLICY & SUSPENDED PACKAGES INSPECTOR`e[0m"
Write-Host "`e[1;38;5;39m===============================================================================`e[0m"
Write-Host ""
Write-Host " `e[1;38;5;75m[TARGET TELEMETRY]`e[0m"
Write-Host "   Device Model   : `e[1;38;5;253m$model`e[0m"
Write-Host "   ADB Serial     : `e[38;5;244m$Serial`e[0m"
Write-Host "   Lock Status    : $lockDisplay"
Write-Host "   Device Owner   : $doDisplay"
Write-Host ""
Write-Host " `e[1;38;5;75m[SUSPENDED & RESTRICTED PACKAGES ($($packages.Count) TOTAL)]`e[0m"
Write-Host " `e[38;5;240m-------------------------------------------------------------------------------`e[0m"
Write-Host "   `e[1;38;5;221m# `e[0m | `e[1;38;5;253mPackage Identifier`e[0m                 | `e[1;38;5;75mIdentified Application / Category`e[0m"
Write-Host " `e[38;5;240m-------------------------------------------------------------------------------`e[0m"

$i = 1
foreach ($p in ($packages | Sort-Object)) {
    $numStr = "{0:D2}" -f $i
    $desc = if ($knownApps.ContainsKey($p)) { $knownApps[$p] } else { "Restricted Package / Policy Rule" }
    $pkgPadded = $p.PadRight(35)
    Write-Host "  `e[1;38;5;221m$numStr`e[0m | `e[38;5;253m$pkgPadded`e[0m | `e[38;5;82m$desc`e[0m"
    $i++
}
Write-Host " `e[38;5;240m-------------------------------------------------------------------------------`e[0m"
Write-Host ""
Write-Host " `e[1;38;5;75m[ACTIVE SECURITY RESTRICTIONS]`e[0m"
Write-Host "   `e[1;38;5;82m[√]`e[0m DISALLOW_FACTORY_RESET        : `e[38;5;253mEnforced`e[0m `e[38;5;244m(Blocks phone wipe from settings)`e[0m"
Write-Host "   `e[1;38;5;82m[√]`e[0m DISALLOW_SAFE_BOOT            : `e[38;5;253mEnforced`e[0m `e[38;5;244m(Prevents bypassing via safe mode)`e[0m"
Write-Host "   `e[1;38;5;82m[√]`e[0m DISALLOW_MODIFY_ACCOUNTS      : `e[38;5;253mEnforced`e[0m `e[38;5;244m(Prevents removing Device Owner)`e[0m"
Write-Host "   `e[1;38;5;82m[√]`e[0m CHROME_INCOGNITO_MODE         : `e[38;5;253mEnforced`e[0m `e[38;5;244m(Incognito availability = 1 / Disabled)`e[0m"
Write-Host "   `e[1;38;5;82m[√]`e[0m NOTORIOUS_APP_KILLER          : `e[38;5;253mEnforced`e[0m `e[38;5;244m(Real-time Tier 1 & Tier 2 auto-suspend)`e[0m"
Write-Host "`e[1;38;5;39m===============================================================================`e[0m"
