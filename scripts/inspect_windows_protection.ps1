$esc = [char]27
$C_HDR  = "$esc[1;38;5;39m"
$C_SEC  = "$esc[1;38;5;75m"
$C_OK   = "$esc[1;38;5;82m"
$C_WARN = "$esc[1;38;5;214m"
$C_ERR  = "$esc[1;38;5;203m"
$R      = "$esc[0m"

Write-Host "$C_HDR===============================================================================$R"
Write-Host " $C_HDR[#] DPCLOCKER :: WINDOWS BROWSER, IFEO, DNS & ENFORCER AUDIT v2$R"
Write-Host "$C_HDR===============================================================================$R`n"

Write-Host " $C_SEC[1. MANAGED BROWSER POLICIES (CHROME & EDGE)]$R"
foreach ($item in @(
    @{ Name="Google Chrome"; Path="HKLM:\SOFTWARE\Policies\Google\Chrome"; Inc="IncognitoModeAvailability" },
    @{ Name="Microsoft Edge"; Path="HKLM:\SOFTWARE\Policies\Microsoft\Edge"; Inc="InPrivateModeAvailability" },
    @{ Name="Brave (Backup)"; Path="HKLM:\SOFTWARE\Policies\BraveSoftware\Brave"; Inc="IncognitoModeAvailability" }
)) {
    $p = Get-ItemProperty -Path $item.Path -ErrorAction SilentlyContinue
    $incOk = ($p.($item.Inc) -eq 1)
    $dohOk = ($p.DnsOverHttpsMode -eq "secure" -and $p.DnsOverHttpsTemplates -like "*cleanbrowsing*")
    $extOk = ($p.ExtensionSettings -like "*vpnProvider*")
    $torInfo = if ($item.Name -like "Brave*") { " | TorDisabled=$($p.TorDisabled) | VPNDisabled=$($p.BraveVPNDisabled)" } else { "" }
    Write-Host "   $($item.Name.PadRight(16)) : IncognitoBlocked=$incOk | CleanBrowsingDoH=$dohOk | BlockVPNExt=$extOk | GuestDisabled=$($p.BrowserGuestModeEnabled -eq 0)$torInfo"
}

Write-Host "`n $C_SEC[2. KERNEL IFEO UNAUTHORIZED BROWSER BLOCKS]$R"
foreach ($exe in @("brave.exe","BraveUpdate.exe","opera.exe","firefox.exe","vivaldi.exe","comet.exe","tor.exe","duckduckgo.exe")) {
    $dbg = (Get-ItemProperty "HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Image File Execution Options\$exe" -Name "Debugger" -ErrorAction SilentlyContinue).Debugger
    $badge = if ($dbg) { "$C_OK[BLOCKED -> $dbg]$R" } else { "$C_ERR[NOT BLOCKED]$R" }
    Write-Host "   $($exe.PadRight(18)) : $badge"
}

Write-Host "`n $C_SEC[3. NETWORK DNS & STUDY SITE RESOLUTION]$R"
foreach ($a in (Get-NetAdapter | Where-Object { $_.Status -eq "Up" })) {
    $dns4 = (Get-DnsClientServerAddress -InterfaceAlias $a.Name -AddressFamily IPv4 -ErrorAction SilentlyContinue).ServerAddresses -join ", "
    $dns6 = (Get-DnsClientServerAddress -InterfaceAlias $a.Name -AddressFamily IPv6 -ErrorAction SilentlyContinue).ServerAddresses -join ", "
    Write-Host "   $($a.Name) IPv4: $dns4 | IPv6: $dns6"
}
foreach ($hostTest in @("cssprep.com.pk","cssprepforum.com","www.google.com","brave.com","search.brave.com","pornhub.com")) {
    $res = (Resolve-DnsName $hostTest -Type A -ErrorAction SilentlyContinue | Where-Object { $_.Type -eq 'A' } | Select-Object -ExpandProperty IPAddress) -join ", "
    if (-not $res) { $res = "NXDOMAIN (Blocked)" }
    Write-Host "   Resolve $hostTest -> $res"
}

Write-Host "`n $C_SEC[4. SYSTEM ENFORCER & INTERACTIVE SENTINEL STATUS]$R"
$task = Get-ScheduledTask -TaskName "DpcLockerSystemEnforcer" -ErrorAction SilentlyContinue
if ($task) {
    Write-Host "   Scheduled Task DpcLockerSystemEnforcer : State=$($task.State) | User=$($task.Principal.UserId)"
} else {
    Write-Host "   Scheduled Task DpcLockerSystemEnforcer : $C_ERR[NOT INSTALLED]$R"
}
$pyProcs = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq "pythonw.exe" -and $_.CommandLine -match "windows_(proxy_sentinel|sentinel_watchdog|sentinel_guard|system_enforcer)" }
Write-Host "   Active Protection Python Processes     : $($pyProcs.Count)"
foreach ($pp in $pyProcs) {
    Write-Host "     PID $($pp.ProcessId): $($pp.CommandLine)"
}
Write-Host "`n$C_HDR===============================================================================$R"
