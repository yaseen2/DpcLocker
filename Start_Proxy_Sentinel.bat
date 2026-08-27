@echo off
setlocal
title DPCLOCKER :: START PROXY SENTINEL

echo ===============================================================================
echo  [#] DPCLOCKER :: STARTING WINDOWS PROXY SENTINEL (BACKGROUND)
echo ===============================================================================
echo.

:: Check if already running
tasklist /fi "imagename eq pythonw.exe" | findstr /i "pythonw.exe" >nul 2>&1
if %errorlevel% equ 0 (
    echo  [*] Sentinel is already running in the background.
    echo.
    ping 127.0.0.1 -n 3 >nul
    exit /b 0
)

set "PYW=pythonw.exe"
if exist "%LOCALAPPDATA%\Programs\Python\Python311\pythonw.exe" (
    set "PYW=%LOCALAPPDATA%\Programs\Python\Python311\pythonw.exe"
)

start "" "%PYW%" "%~dp0windows_proxy_sentinel.py"
echo  [+] Windows Proxy Sentinel is now actively monitoring all browsers in background!
echo  [+] If 'proxy', 'unblock', or proxy sites are searched or opened, the tab closes instantly.
echo.
ping 127.0.0.1 -n 3 >nul
