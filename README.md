# DPC Locker 🔒

**Zero-Trust, Impulse-Proof Android Device Owner Lock & Cross-Platform Self-Control System**

DPC Locker is a unified Android protection system paired with Windows Registry policies. It merges **Test DPC (Device Policy Controller)** with an **Impulse-Proof USB ADB Guard**, an **Auto Non-Chrome Browser Blocker**, and an **Impulse-Proof Daily App Timer System (Family Link style)**. 

Policy modifications, app timer changes, or disabling protection **can only be performed when physically connected to a PC via USB ADB cable**.

---

## 🎯 Key Features & Architecture

### 📱 1. Unified Android DPC Protection Architecture
* **Single Unified DPC App:** Test DPC and DPC Locker guard are merged into a single Android Device Owner application (`com.afwsamples.testdpc`).
* **Zero Accessibility Service Required:** Eliminates the need for external accessibility watchdogs or extra app permissions. 100% native Device Owner protection.
* **Laser-Targeted Protection:** 
  * Prevents accessing policy configuration screens inside Test DPC when locked.
  * **Leaves 100% of standard phone settings (Wi-Fi, Bluetooth, Display, Sound, Battery, Storage, Apps, etc.) completely accessible.**
* **USB ADB State Control:** Lock state is toggled via system setting `dpclocker_enabled` using 1-click batch scripts over USB ADB (`Lock_TestDPC.bat` and `Unlock_TestDPC.bat`).
* **100% Offline & Private:** Zero internet permissions declared in guard; 0% data tracking.

---

### 🌐 2. Pure Dynamic Auto Non-Chrome Browser Blocker
* **Real-time `LauncherApps.Callback` Engine:** Uses Android's native system callback to inspect new app installations in real time.
* **Pure Dynamic Intent Filter Inspection (`MATCH_ALL`):** Dynamically detects third-party web browsers (Opera Mini, Firefox, Brave, UC Browser, Phoenix Browser, DuckDuckGo, Tor, Vivaldi, Kiwi, etc.) by inspecting generic `http://` and `https://` `BROWSABLE` intent filters **without relying on hardcoded package lists**.
* **Instant Auto-Freeze:** Automatically suspends third-party browsers (`dpm.setPackagesSuspended([packageName], true)`) immediately upon installation, graying out their app icons.
* **100% Freedom for Normal Apps:** Games, social media, shopping apps, tools, banking apps, and messaging apps (WhatsApp, Telegram, etc.) do not claim generic web intents and remain **100% active and unrestricted**.
* **Chrome & Google App Whitelist:** Google Chrome (`com.android.chrome`), Google App (`com.google.android.googlequicksearchbox`), and Play Store (`com.android.vending`) remain fully functional while guarded by Strict SafeSearch, SafeSites adult filter, and Private DNS.
* **UI Control Switch:** Includes an `Auto-Block Non-Chrome Browsers` preference switch in Test DPC's UI (backed by app-private `SharedPreferences` for 0-crash stability).

---

### ⏱️ 3. Impulse-Proof App Daily Usage Timers (Family Link Style)
* **Native Android System Observers (`UsageStatsManager`):** Monitors daily foreground screen-on usage per package using Android's native `registerAppUsageObserver` system API.
* **Zero Battery Drain (0% CPU):** The Android OS kernel tracks foreground app usage natively. No background polling loops or timers are used.
* **Automatic App Suspension on Limit:** The moment an app reaches its configured daily limit (e.g., 30 minutes for YouTube, 15 minutes for Instagram), Android OS notifies Test DPC, which **instantly freezes the app**.
* **12:00 AM Midnight Auto-Reset:** An `AlarmManager` daily alarm automatically resets usage counters and unsuspends all apps every night at midnight (12:00 AM).
* **Impulse-Proof Protection:** Because Test DPC is locked via `Lock_TestDPC.bat`, you cannot open Test DPC on your phone to grant yourself "5 more minutes" on impulse.
* **App UI Dialog:** Includes an interactive **App Daily Usage Timers** dialog in Test DPC where you can view today's usage (e.g. `YouTube (22m used / 30m limit)`) and set daily minute limits for any installed application.

---

### 💻 4. Windows 10/11 PC Protection Architecture
* **System-Wide Adult Content DNS Filter:** Sets Wi-Fi & Ethernet DNS to CleanBrowsing Family Filter (`185.228.168.168` / `185.228.169.168`) to block adult domains across all Windows apps.
* **Google & Bing SafeSearch Hardening (`hosts` File):** Maps Google & Bing to Strict SafeSearch IP (`216.239.38.120`), and maps custom domains (e.g. `fboxtv.org`) to `0.0.0.0`.
* **Chrome & Edge Registry Policies:** Disables Incognito in Chrome and InPrivate in Edge, forces Strict SafeSearch, enables Chrome SafeSites adult filter, and blocks custom domains via `URLBlocklist`.
* **Windows VPN & Proxy Lock:** Disables adding new VPN connections or proxy servers in Windows Settings, and disables the Windows `RasMan` Remote Access VPN service.
* **Productivity Friendly:** Normal Chrome and Edge extensions remain 100% allowed so daily productivity tools continue working uninterrupted.

---

## 📁 Repository Structure

```text
├── testdpc_source/                     # Merged Single App Source Code (Test DPC + Guard + Blocker + Timers)
│   └── app/src/main/java/com/afwsamples/testdpc/
│       ├── PolicyManagementActivity.java # Main DPC Activity with USB ADB Lock Guard
│       ├── SetupManagementActivity.java  # Setup Activity with USB ADB Lock Guard
│       ├── BrowserBlocker.java           # Pure Dynamic Auto Non-Chrome Browser Blocker Engine
│       ├── PackageInstallReceiver.java   # Real-Time Package Install BroadcastReceiver
│       ├── AppTimerManager.java          # Impulse-Proof App Usage Limits & System Observers Engine
│       ├── AppTimerReceiver.java         # Daily Limit Exceeded & Midnight Reset Receiver
│       ├── DeviceAdminReceiver.java      # Device Owner Receiver & System Boot Listener
│       └── policy/
│           └── PolicyManagementFragment.java # Test DPC UI with Auto-Blocker Switch & App Timers Dialog
├── Lock_TestDPC.bat                    # 1-Click USB ADB Script: Lock Test DPC & Protection
├── Unlock_TestDPC.bat                  # 1-Click USB ADB Script: Unlock Test DPC for Maintenance
├── build_merged_dpc.ps1                # PowerShell Script to compile Test DPC APK
├── enable_windows_protection.ps1       # Windows PowerShell Script (SafeSearch, CleanBrowsing DNS & VPN Lock)
├── enable_windows_protection.reg       # Windows Registry (.reg) Policy Export
└── README.md                           # Comprehensive Documentation
```

---

## 🛠️ Complete Setup Guide

### 1. Windows Setup (Adult Content, Incognito & VPN Locked)

Open PowerShell as Administrator and run `enable_windows_protection.ps1` (or double-click `enable_windows_protection.reg`).

**Applied System Policies:**
* **CleanBrowsing Family DNS:** Sets system DNS to `185.228.168.168` and `185.228.169.168` (blocks adult domains system-wide).
* **System Hosts Overrides:** Maps Google & Bing to Strict SafeSearch IP (`216.239.38.120`), and maps `fboxtv.org` & `www.fboxtv.org` to `0.0.0.0`.
* **Windows VPN & Proxy Lock:** Disables adding new VPN connections or proxy servers in Windows Settings.
* **Disables Windows RasMan Service:** Prevents starting the Windows Remote Access VPN service.
* **Chrome & Edge Registry Policies:**
  * `IncognitoModeAvailability` = `1` *(Disables Incognito)*
  * `InPrivateModeAvailability` = `1` *(Disables InPrivate)*
  * `ForceGoogleSafeSearch` = `1` *(Forces Strict SafeSearch)*
  * `SafeSitesFilterBehavior` = `1` *(Enforces Chrome adult site filter)*
  * `URLBlocklist` = `["*fboxtv.org*"]` *(Blocks custom target domains)*

---

### 2. Android Device Owner Provisioning (Test DPC)

1. Build or install the merged **Test DPC** APK (`TestDPC-normal-debug.apk`) on your Android phone.
2. Remove all secondary user profiles and Google accounts from phone settings temporarily during provisioning.
3. Connect phone to PC via USB ADB and provision Test DPC as Device Owner:
   ```cmd
   adb shell dpm set-device-owner com.afwsamples.testdpc/.DeviceAdminReceiver
   ```
4. Re-add your Google accounts.

---

### 3. Key Test DPC Policies & Managed Configurations

Connect phone via USB ADB and run `Unlock_TestDPC.bat` to open Test DPC:

#### ⚙️ Managed Configurations (Chrome Policy)
* **Chrome Incognito:** `IncognitoModeAvailability` = `1` *(Disables Incognito Mode)*
* **Strict Google SafeSearch:** `ForceGoogleSafeSearch` = `true` & `SafeSearchMode` = `1` *(Removes explicit images & unblur toggle)*
* **Chrome Adult Site Filtering:** `SafeSitesFilterBehavior` = `1` *(Enforces Chrome's built-in SafeSites adult content filter)*
* **Chrome Domain Blocklist (`URLBlocklist`):** `String[]` -> `["fboxtv.org"]` *(Blocks specific websites and subdomains in Chrome)*

#### 🔒 Critical User Restrictions (In Test DPC)
* **`Disallow uninstall apps` (`DISALLOW_UNINSTALL_APPS`):** Grays out and disables the "Uninstall" button for all apps on the phone UI.
* **`Disallow apps control` (`DISALLOW_APPS_CONTROL`):** Prevents clearing app data, modifying app permissions, or force-stopping apps in Android Settings.
* **`Disallow install from unknown sources` (`DISALLOW_INSTALL_UNKNOWN_SOURCES`):** Blocks installing APK files from outside the Google Play Store.
* **`Disallow config private DNS` (`DISALLOW_CONFIG_PRIVATE_DNS`):** Locks system Private DNS settings so the CleanBrowsing adult filter cannot be changed.

---

### 4. Private DNS System-Wide Adult Filter

On your phone, go to **Settings > Network & Internet > Private DNS**:
* Select **Private DNS provider hostname** and enter:
  `family-filter-dns.cleanbrowsing.org`

---

## ⚡ Daily USB ADB Operating Workflow

* **🔒 To Lock Everything (Default Protection State):**  
  Double-click `Lock_TestDPC.bat`. Opening Test DPC will display:
  > *"Protection Active! Connect via USB ADB to unlock."*
  and instantly close, preventing any policy, timer, or browser blocker changes on impulse.

* **🔓 To Maintenance / Update Policies & Timers:**  
  Plug your phone into PC via USB cable and double-click `Unlock_TestDPC.bat`. Open Test DPC on your phone to configure policies, adjust app timers, or toggle settings. When finished, double-click `Lock_TestDPC.bat`.

---

## 🔨 Building Test DPC from Source

You can build the merged APK directly from PowerShell using local Gradle:

```powershell
powershell -ExecutionPolicy Bypass -File "build_merged_dpc.ps1"
```

The compiled APK will be created at:
`testdpc_source\app\build\outputs\apk\normal\debug\TestDPC-normal-debug.apk`

---

## 📄 License

Open-source under the MIT License. Designed for personal self-control, digital wellness, and enterprise policy enforcement.
