# DPC Locker 🔒

**Zero-Trust, Impulse-Proof Android Device Owner Lock & Cross-Platform Self-Control System**

DPC Locker is a unified Android protection system paired with Windows Registry policies. It merges **Test DPC (Device Policy Controller)** with an **Impulse-Proof USB ADB Guard**, an **Auto Non-Chrome Browser Blocker**, an **Auto Notorious App Installation Blocker**, and an **Impulse-Proof Daily App Timer System (Family Link style)**. 

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

### 🌐 2. Pure Dynamic Auto Non-Chrome Browser Blocker & Notorious App Blocker
* **Real-time `LauncherApps.Callback` Engine:** Uses Android's native system callback to inspect new app installations in real time.
* **Pure Dynamic Intent Filter Inspection (`MATCH_ALL`):** Dynamically detects third-party web browsers (Opera Mini, Firefox, Brave, UC Browser, Phoenix Browser, DuckDuckGo, Tor, Vivaldi, Kiwi, etc.) by inspecting generic `http://` and `https://` `BROWSABLE` intent filters **without relying on hardcoded package lists**.
* **Notorious App Auto-Freezer:** Auto-detects and freezes installation of notorious apps (X/Twitter, Reddit, Tumblr, Telegram) immediately upon installation while allowing uninstallation.
* **Chrome & Google App Whitelist:** Google Chrome (`com.android.chrome`), Google App (`com.google.android.googlequicksearchbox`), and Play Store (`com.android.vending`) remain fully functional while guarded by Strict SafeSearch and SafeSites adult filtering.
* **UI Control Switch & Package Blocklist:** Includes an `Auto-Block Non-Chrome Browsers` preference switch and an interactive `Blocked Apps & Package Blocklist` dialog in Test DPC's UI.

---

### ⏱️ 3. Impulse-Proof App Daily Usage Timers (Family Link Style)
* **Native Android System Observers (`UsageStatsManager`):** Monitors daily foreground screen-on usage per package using Android's native `registerAppUsageObserver` system API.
* **Zero Battery Drain (0% CPU):** The Android OS kernel tracks foreground app usage natively. No background polling loops or timers are used.
* **Automatic App Suspension on Limit:** The moment an app reaches its configured daily limit (e.g., 30 minutes for YouTube, 15 minutes for Instagram), Android OS notifies Test DPC, which **instantly freezes the app**.
* **Un-Uninstallable Protection:** Apps with active timers are marked as un-uninstallable (`setUninstallBlocked`). Reinstalling an app during the same day instantly re-suspends it upon installation.
* **12:00 AM Midnight Auto-Reset:** An `AlarmManager` daily alarm automatically resets usage counters and unsuspends all apps every night at midnight (12:00 AM).

---

### 🤖 4. Experimental On-Device AI Screen Shield (Toggleable)
* **On-Device Vision Classifier (`ExperimentalAiScanner.java`):** Embedded lightweight HSV color space and skin-density heuristic vision classifier for analyzing screen buffer frames.
* **UI Toggleable (OFF by default):** Includes an `Experimental AI Screen Shield` switch in Test DPC's UI. Remains completely OFF by default to preserve 100% battery life and 0% CPU usage.

---

### 💻 5. Windows 10/11 PC Protection Architecture
* **Google & Bing SafeSearch Hardening (`hosts` File):** Maps Google & Bing to Strict SafeSearch IP (`216.239.38.120`).
* **Total Notorious Domain Lockdown:** Maps X (`x.com`, `twitter.com`, `twimg.com`), Reddit (`reddit.com`, `redditmedia.com`, `redd.it`), Tumblr (`tumblr.com`), Telegram Web (`telegram.org`, `t.me`), and Web Proxies (`croxyproxy.com`, `proxysite.com`, `hide.me`, `blockaway.net`) to `0.0.0.0` in system `hosts` file and adds wildcard entries to Chrome & Edge `URLBlocklist`. *(Discord is allowed)*.
* **Chrome & Edge Registry Policies:**
  * **`ForceGoogleSafeSearch`**: Forces Strict Google SafeSearch system-wide in Chrome.
  * **`SafeSitesFilterBehavior`**: Enforces Chrome's built-in SafeSites adult content filter for all web traffic.
  * **`IncognitoModeAvailability` / `InPrivateModeAvailability`**: Disables Incognito in Chrome & InPrivate in Edge.
  * **`DnsOverHttpsMode` = `"off"`**: Disables Chrome's Secure DNS bypass to enforce system `hosts` policy.
* **Windows VPN & Proxy Lock (`DISALLOW_CONFIG_VPN`):** Disables adding new VPN connections or proxy servers in Windows Settings, and disables the Windows `RasMan` Remote Access VPN service.

---

## 📁 Repository Structure

```text
├── testdpc_source/                     # Merged Single App Source Code (Test DPC + Guard + Blocker + Timers + AI Shield)
│   └── app/src/main/java/com/afwsamples/testdpc/
│       ├── PolicyManagementActivity.java # Main DPC Activity with USB ADB Lock Guard
│       ├── SetupManagementActivity.java  # Setup Activity with USB ADB Lock Guard
│       ├── BrowserBlocker.java           # Pure Dynamic Auto Non-Chrome Browser Blocker Engine
│       ├── NotoriousAppBlocker.java      # Notorious App Installation Blocker & Package Blocklist Engine
│       ├── ChromePolicyManager.java      # Android Chrome Default Policy & URLBlocklist Engine
│       ├── ExperimentalAiScanner.java    # Toggleable On-Device AI Vision Classifier Engine
│       ├── AppTimerManager.java          # Impulse-Proof App Usage Limits & System Observers Engine
│       ├── AppTimerReceiver.java         # Daily Limit Exceeded & Midnight Reset Receiver
│       ├── DeviceAdminReceiver.java      # Device Owner Receiver & System Boot Listener
│       └── policy/
│           └── PolicyManagementFragment.java # Test DPC UI with Auto-Blockers, Blocklist Dialog & AI Shield
├── Lock_TestDPC.bat                    # 1-Click USB ADB Script: Lock Test DPC & Protection
├── Unlock_TestDPC.bat                  # 1-Click USB ADB Script: Unlock Test DPC for Maintenance
├── Enable_Windows_Protection.bat       # 1-Click Administrator Script: Apply Windows Protection Policies
├── build_merged_dpc.ps1                # PowerShell Script to compile Test DPC APK
├── enable_windows_protection.ps1       # Windows PowerShell Script (SafeSearch, Cloudflare DNS, Notorious Domain Block & VPN Lock)
├── enable_windows_protection.reg       # Windows Registry (.reg) Policy Export
└── README.md                           # Comprehensive Documentation
```

---

## 🛠️ Complete Setup Guide

### 1. Windows Setup (Adult Content, SafeSearch, Notorious Domain Block & VPN Lock)

Right-click **`Enable_Windows_Protection.bat`** > **Run as Administrator** (or run `enable_windows_protection.ps1` in Admin PowerShell).

---

### 2. Core Essential Chrome & Android Policies

When managing policies inside Test DPC (`Unlock_TestDPC.bat`), the primary enforced policies are:

#### ⚙️ Managed Configurations (App Restrictions for Chrome)
1. **`ForceGoogleSafeSearch` = `true` / `1`**: Forces Strict Google SafeSearch system-wide in Google Chrome.
2. **`SafeSitesFilterBehavior` = `1`**: Enables Chrome's built-in SafeSites automatic adult content filter for all browsing traffic.
3. **`URLBlocklist`**: `["fboxtv.org", "x.com", "twitter.com", "twimg.com", "reddit.com", "redditmedia.com", "redd.it", "tumblr.com", "telegram.org", "t.me"]`.

---

## ⚡ Daily USB ADB Operating Workflow

* **🔒 To Lock Everything (Default Protection State):**  
  Double-click `Lock_TestDPC.bat`. Opening Test DPC will display:
  > *"Protection Active! Connect via USB ADB to unlock."*
  and instantly close.

* **🔓 To Maintenance / Update Policies & Timers:**  
  Plug your phone into PC via USB cable and double-click `Unlock_TestDPC.bat`. Open Test DPC on your phone to configure policies, adjust app timers, or toggle settings. When finished, double-click `Lock_TestDPC.bat`.

---

## 📄 License

Open-source under the MIT License. Designed for personal self-control, digital wellness, and enterprise policy enforcement.
