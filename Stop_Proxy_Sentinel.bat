@echo off
setlocal
title DPCLOCKER :: STOP PROXY SENTINEL

echo ===============================================================================
echo  [#] DPCLOCKER :: STOPPING WINDOWS PROXY SENTINEL
echo ===============================================================================
echo.

taskkill /f /im pythonw.exe >nul 2>&1
echo  [+] Windows Proxy Sentinel has been stopped.
echo.
ping 127.0.0.1 -n 3 >nul
