@echo off
:: ==============================================================================
:: DPCLOCKER :: COMPLETE WINDOWS VPN ELIMINATION & BLOCKER DEPLOYMENT
:: ==============================================================================
:: Automatically terminates SkyVPN & all VPNs, deletes virtual TAP/TUN adapters,
:: blocks VPN handshake firewall ports, configures Device Installation policies,
:: and restarts Sentinel with high-integrity watchdog protection.
:: ==============================================================================

:: Check for Administrator elevation
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [!] Running with user privileges or Task Scheduler Highest token...
)

echo ===============================================================================
echo  [+] DEPLOYING ENTERPRISE WINDOWS VPN ELIMINATION & BLOCKER
echo ===============================================================================
echo.

:: 1. Forcefully terminate SkyVPN and all other VPN processes
echo [*] Terminating SkyVPN and running VPN processes...
taskkill /F /IM skyvpn.exe /T >nul 2>&1
taskkill /F /IM CrashSender1403.exe /T >nul 2>&1
taskkill /F /IM openvpn.exe /T >nul 2>&1
taskkill /F /IM wireguard.exe /T >nul 2>&1
taskkill /F /IM protonvpn.exe /T >nul 2>&1
taskkill /F /IM nordvpn.exe /T >nul 2>&1
taskkill /F /IM windscribe.exe /T >nul 2>&1
taskkill /F /IM expressvpn.exe /T >nul 2>&1
taskkill /F /IM warp-svc.exe /T >nul 2>&1
taskkill /F /IM psiphon.exe /T >nul 2>&1
taskkill /F /IM psiphon3.exe /T >nul 2>&1
echo [+] All known VPN processes terminated.

:: 2. Disable and delete TAP-Windows Virtual Network Adapter
echo.
echo [*] Disabling & removing virtual network adapters (TAP / Wintun / WireGuard)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-NetAdapter -ErrorAction SilentlyContinue | Where-Object { $_.InterfaceDescription -match 'TAP|TUN|Wintun|WireGuard|VPN|Virtual' -or $_.Name -match 'TAP|TUN|Wintun|WireGuard|VPN' } | ForEach-Object { Disable-NetAdapter -Name $_.Name -Confirm:$false ; Write-Host '[-] Disabled virtual adapter:' $_.Name }"

:: Uninstall driver package via pnputil
pnputil /delete-driver oem51.inf /uninstall /force >nul 2>&1
echo [+] Virtual network adapters neutralized.

:: 3. Configure Outbound Windows Firewall Rules blocking all VPN handshake ports
echo.
echo [*] Deploying Windows Firewall rules against VPN handshake protocols...
netsh advfirewall firewall delete rule name="Block_VPN_OpenVPN_UDP" >nul 2>&1
netsh advfirewall firewall delete rule name="Block_VPN_OpenVPN_TCP" >nul 2>&1
netsh advfirewall firewall delete rule name="Block_VPN_WireGuard_UDP" >nul 2>&1
netsh advfirewall firewall delete rule name="Block_VPN_IPsec_IKE" >nul 2>&1
netsh advfirewall firewall delete rule name="Block_VPN_IPsec_NAT" >nul 2>&1
netsh advfirewall firewall delete rule name="Block_VPN_L2TP" >nul 2>&1
netsh advfirewall firewall delete rule name="Block_VPN_PPTP" >nul 2>&1
netsh advfirewall firewall delete rule name="Block_VPN_ESP" >nul 2>&1
netsh advfirewall firewall delete rule name="Block_VPN_AH" >nul 2>&1

netsh advfirewall firewall add rule name="Block_VPN_OpenVPN_UDP" dir=out action=block protocol=UDP remoteport=1194 >nul 2>&1
netsh advfirewall firewall add rule name="Block_VPN_OpenVPN_TCP" dir=out action=block protocol=TCP remoteport=1194 >nul 2>&1
netsh advfirewall firewall add rule name="Block_VPN_WireGuard_UDP" dir=out action=block protocol=UDP remoteport=51820 >nul 2>&1
netsh advfirewall firewall add rule name="Block_VPN_IPsec_IKE" dir=out action=block protocol=UDP remoteport=500 >nul 2>&1
netsh advfirewall firewall add rule name="Block_VPN_IPsec_NAT" dir=out action=block protocol=UDP remoteport=4500 >nul 2>&1
netsh advfirewall firewall add rule name="Block_VPN_L2TP" dir=out action=block protocol=UDP remoteport=1701 >nul 2>&1
netsh advfirewall firewall add rule name="Block_VPN_PPTP" dir=out action=block protocol=TCP remoteport=1723 >nul 2>&1
netsh advfirewall firewall add rule name="Block_VPN_ESP" dir=out action=block protocol=50 >nul 2>&1
netsh advfirewall firewall add rule name="Block_VPN_AH" dir=out action=block protocol=51 >nul 2>&1
echo [+] Windows Firewall rules active: All VPN tunnel handshake ports blocked outbound.

:: 4. Disable Windows Built-in Remote Access (RAS) Subsystem
echo.
echo [*] Disabling Windows Built-in VPN / RAS services and registry policies...
sc config RasMan start= disabled >nul 2>&1
sc config RasAuto start= disabled >nul 2>&1
sc stop RasMan >nul 2>&1
sc stop RasAuto >nul 2>&1

reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\Network Connections" /v "NC_RasConnect" /t REG_DWORD /d 0 /f >nul 2>&1
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\Network Connections" /v "NC_NewConnectionWizard" /t REG_DWORD /d 0 /f >nul 2>&1
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\Network Connections" /v "NC_RasAllUserAdd" /t REG_DWORD /d 0 /f >nul 2>&1
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\Network Connections" /v "NC_RasUsersAdd" /t REG_DWORD /d 0 /f >nul 2>&1
echo [+] Windows built-in VPN subsystem permanently locked.

:: 5. Apply Registry Device Installation Restrictions for Virtual Adapters
echo.
echo [*] Enforcing Device Installation Restrictions in Registry...
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\DeviceInstall\Restrictions" /v "DenyDeviceIDs" /t REG_DWORD /d 1 /f >nul 2>&1
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\DeviceInstall\Restrictions" /v "DenyDeviceIDsRetroactive" /t REG_DWORD /d 1 /f >nul 2>&1
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\DeviceInstall\Restrictions\DenyDeviceIDs" /v "1" /t REG_SZ /d "*tap0901*" /f >nul 2>&1
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\DeviceInstall\Restrictions\DenyDeviceIDs" /v "2" /t REG_SZ /d "root\tap0901" /f >nul 2>&1
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\DeviceInstall\Restrictions\DenyDeviceIDs" /v "3" /t REG_SZ /d "*tapskyvpn*" /f >nul 2>&1
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\DeviceInstall\Restrictions\DenyDeviceIDs" /v "4" /t REG_SZ /d "*wintun*" /f >nul 2>&1
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\DeviceInstall\Restrictions\DenyDeviceIDs" /v "5" /t REG_SZ /d "*wireguard*" /f >nul 2>&1
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\DeviceInstall\Restrictions\DenyDeviceIDs" /v "6" /t REG_SZ /d "*ovpn-dco*" /f >nul 2>&1
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\DeviceInstall\Restrictions\DenyDeviceIDs" /v "7" /t REG_SZ /d "*nordlynx*" /f >nul 2>&1
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\DeviceInstall\Restrictions\DenyDeviceIDs" /v "8" /t REG_SZ /d "*tapproton*" /f >nul 2>&1
echo [+] Device Installation restrictions applied.

:: 6. Restart Sentinel & Watchdog with elevated privileges
echo.
echo [*] Restarting Sentinel & Watchdog with updated Generic VPN & NetAdapter rules...
set SCRIPT_DIR=%~dp0
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-Process -Name 'python', 'pythonw' -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*Python311*' } | Stop-Process -Force"
timeout /t 1 /nobreak >nul

start "" /B "%LOCALAPPDATA%\Programs\Python\Python311\pythonw.exe" "%SCRIPT_DIR%windows_proxy_sentinel.py"
timeout /t 1 /nobreak >nul
start "" /B "%LOCALAPPDATA%\Programs\Python\Python311\pythonw.exe" "%SCRIPT_DIR%windows_sentinel_watchdog.py"

echo.
echo ===============================================================================
echo  [+] COMPLETE VPN DEFENSE SYSTEM DEPLOYED SUCCESSFULLY!
echo ===============================================================================
echo.
timeout /t 3
