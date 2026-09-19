@echo off
setlocal EnableDelayedExpansion
title DPCLOCKER :: ENFORCE MUSIC & ONLINEVPN.APP BLOCKER

:: Check for Administrator elevation
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [*] Elevating to Administrator to apply Windows policies and hosts sinkhole...
    powershell -NoProfile -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b 0
)

echo ===============================================================================
echo  [+] DPCLOCKER :: ENFORCING MUSIC & ONLINEVPN.APP SYSTEM-WIDE BLOCKER
echo ===============================================================================
echo.
echo  Targets:
echo   - onlinevpn.app (Online VPN Browser App)
echo   - YouTube Music (music.youtube.com - standard YouTube preserved)
echo   - Spotify (Desktop Client + Web Player)
echo   - SoundCloud, Apple Music, Amazon Music, Deezer, Tidal, Pandora, Gaana, etc.
echo.

echo  [1/3] Injecting Enterprise URLBlocklist policies (Edge, Chrome, Brave)...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Apply_Adult_URLBlocklist.ps1"
if %errorlevel% neq 0 (
    echo  [-] Warning: Failed to apply URLBlocklist policies.
) else (
    echo  [+] Enterprise URLBlocklist policies successfully updated.
)
echo.

echo  [2/3] Updating Windows hosts file DNS sinkhole...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$hostsPath = '$env:SystemRoot\System32\drivers\etc\hosts';" ^
    "$entries = @(" ^
    "    '0.0.0.0 onlinevpn.app', '0.0.0.0 www.onlinevpn.app'," ^
    "    '0.0.0.0 music.youtube.com'," ^
    "    '0.0.0.0 spotify.com', '0.0.0.0 www.spotify.com', '0.0.0.0 open.spotify.com', '0.0.0.0 play.spotify.com'," ^
    "    '0.0.0.0 soundcloud.com', '0.0.0.0 www.soundcloud.com'," ^
    "    '0.0.0.0 music.apple.com'," ^
    "    '0.0.0.0 music.amazon.com'," ^
    "    '0.0.0.0 deezer.com', '0.0.0.0 www.deezer.com'," ^
    "    '0.0.0.0 tidal.com', '0.0.0.0 www.tidal.com', '0.0.0.0 listen.tidal.com'," ^
    "    '0.0.0.0 pandora.com', '0.0.0.0 www.pandora.com'," ^
    "    '0.0.0.0 gaana.com', '0.0.0.0 www.gaana.com'," ^
    "    '0.0.0.0 jiosaavn.com', '0.0.0.0 www.jiosaavn.com', '0.0.0.0 saavn.com', '0.0.0.0 www.saavn.com'," ^
    "    '0.0.0.0 wynk.in', '0.0.0.0 www.wynk.in'," ^
    "    '0.0.0.0 hungama.com', '0.0.0.0 www.hungama.com'," ^
    "    '0.0.0.0 bandcamp.com'," ^
    "    '0.0.0.0 audiomack.com', '0.0.0.0 www.audiomack.com'," ^
    "    '0.0.0.0 qobuz.com', '0.0.0.0 www.qobuz.com'," ^
    "    '0.0.0.0 napster.com', '0.0.0.0 www.napster.com'," ^
    "    '0.0.0.0 tunein.com', '0.0.0.0 www.tunein.com'," ^
    "    '0.0.0.0 iheart.com', '0.0.0.0 www.iheart.com'," ^
    "    '0.0.0.0 last.fm', '0.0.0.0 www.last.fm'," ^
    "    '0.0.0.0 mixcloud.com', '0.0.0.0 www.mixcloud.com'," ^
    "    '0.0.0.0 thepiratebay.org', '0.0.0.0 www.thepiratebay.org'," ^
    "    '0.0.0.0 thepiratesproxy.org', '0.0.0.0 www.thepiratesproxy.org'," ^
    "    '0.0.0.0 tpb.party', '0.0.0.0 www.tpb.party'," ^
    "    '0.0.0.0 1337x.to', '0.0.0.0 www.1337x.to'," ^
    "    '0.0.0.0 yts.mx', '0.0.0.0 www.yts.mx'," ^
    "    '0.0.0.0 torrentgalaxy.to', '0.0.0.0 www.torrentgalaxy.to'" ^
    ");" ^
    "if (Test-Path $hostsPath) {" ^
    "    $existing = Get-Content $hostsPath -Raw;" ^
    "    $toAppend = @();" ^
    "    foreach ($entry in $entries) {" ^
    "        $domain = ($entry -split '\s+')[1];" ^
    "        if ($existing -notmatch ('\b' + [regex]::Escape($domain) + '\b')) {" ^
    "            $toAppend += $entry;" ^
    "        }" ^
    "    }" ^
    "    if ($toAppend.Count -gt 0) {" ^
    "        Add-Content -Path $hostsPath -Value ($toAppend -join [Environment]::NewLine);" ^
    "        Write-Host (' [+] Appended ' + $toAppend.Count + ' new music/VPN entries to hosts file.') -ForegroundColor Green;" ^
    "    } else {" ^
    "        Write-Host ' [*] All music/VPN domains already present in hosts file.' -ForegroundColor Yellow;" ^
    "    }" ^
    "}"
echo.

echo  [3/3] Restarting Sentinel Triad Mesh with updated real-time interception rules...
call "%~dp0Stop_Proxy_Sentinel.bat" >nul 2>&1
%SystemRoot%\System32\PING.EXE 127.0.0.1 -n 2 >nul
wscript.exe //nologo "%~dp0launch_sentinel_hidden.vbs"

echo.
echo ===============================================================================
echo  [+] COMPLETE: Music platforms and onlinevpn.app are now strictly blocked!
echo  [+] Regular YouTube (youtube.com) remains 100%% accessible and functional.
echo ===============================================================================
echo.
pause
