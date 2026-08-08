# DPC Locker 🔒

**Zero-Trust, Impulse-Proof Android Device Owner Lock & Cross-Platform Self-Control System**

DPC Locker is a unified Android protection system paired with Windows Registry policies. It merges **Test DPC (Device Policy Controller)** with an **Impulse-Proof USB ADB Guard**, an **AI-Powered Real-Time App Security Auditor**, a **Pure Dynamic Non-Chrome Browser Blocker**, and an **Impulse-Proof Daily App Timer System (Family Link style)**. 

Policy modifications, app timer changes, or disabling protection **can only be performed when physically connected to a PC via USB ADB cable**.

---

## 🎯 Key Features & Architecture

### 🤖 1. Real-Time Gemini AI App Security Auditor Engine
* **Automated Installation Inspection:** Uses Android's native `LauncherApps.Callback` engine to inspect every newly installed package in real time upon installation from Google Play Store or APK.
* **Deep Structural Payload Analysis:** Extracts complete package metadata: Package Name, App Title, Declared Category (`ApplicationInfo.category`), Requested Permissions list (`PackageInfo.requestedPermissions`), and Internal Activity Classnames (`PackageInfo.activities`).
* **High-Context Gemini 1.5/2.0 Flash REST API:** Asynchronously queries Google's Gemini Flash API using `response_mime_type: "application/json"`. The prompt incorporates explicit system roles detailing the high-stakes impact of classification (preventing false positives on WhatsApp, banking, utilities, and games vs. catching hidden adult browsers and video downloaders).
* **Sub-Second Auto-Freeze:** If Gemini returns `is_risky: true`, DPC Locker executes `dpm.setPackagesSuspended([packageName], true)` in under 1 second, freezing the app before it can ever be opened.
* **Zero Daily Phone Latency:** Runs only once at app installation; daily browsing, streaming, and app usage run at 100% full native speed.
* **UI API Key Preference:** Includes a configuration dialog in Test DPC settings to set/update your free Gemini API Key from Google AI Studio.

---

### 📱 2. Unified Android DPC Protection Architecture
* **Single Unified DPC App:** Test DPC and DPC Locker guard are merged into a single Android Device Owner application (`com.afwsamples.testdpc`).
* **Zero Accessibility Service Required:** Eliminates the need for external accessibility watchdogs or extra app permissions. 100% native Device Owner protection.
* **Laser-Targeted Protection:** 
  * Prevents accessing policy configuration screens inside Test DPC when locked.
  * **Leaves 100% of standard phone settings (Wi-Fi, Bluetooth, Display, Sound, Battery, Storage, Apps, etc.) completely accessible.**
* **USB ADB State Control:** Lock state is toggled via system setting `dpclocker_enabled` using 1-click batch scripts over USB ADB (`Lock_TestDPC.bat` and `Unlock_TestDPC.bat`).
* **100% Offline & Private:** Zero third-party telemetry; 0% data tracking.

---

### 🌐 3. Pure Dynamic Auto Non-Chrome Browser Blocker
* **Real-time `LauncherApps.Callback` Engine:** Uses Android's native system callback to inspect new app installations in real time.
* **Pure Dynamic Intent Filter Inspection (`MATCH_ALL`):** Dynamically detects third-party web browsers (Opera Mini, Firefox, Brave, UC Browser, Phoenix Browser, DuckDuckGo, Tor, Vivaldi, Kiwi, etc.) by inspecting generic `http://` and `https://` `BROWSABLE` intent filters **without relying on hardcoded package lists**.
* **Instant Auto-Freeze:** Automatically suspends third-party browsers (`dpm.setPackagesSuspended([packageName], true)`) immediately upon installation, graying out their app icons.
* **100% Freedom for Normal Apps:** Games, social media, shopping apps, tools, banking apps, and messaging apps (WhatsApp, Telegram, etc.) do not claim generic web intents and remain **100% active and unrestricted**.
* **Chrome & Google App Whitelist:** Google Chrome (`com.android.chrome`), Google App (`com.google.android.googlequicksearchbox`), and Play Store (`com.android.vending`) remain fully functional while guarded by Strict SafeSearch and SafeSites adult filtering.

---

### ⏱️ 4. Impulse-Proof App Daily Usage Timers (Family Link Style)
* **Native Android System Observers (`UsageStatsManager`):** Monitors daily foreground screen-on usage per package using Android's native `registerAppUsageObserver` system API.
* **Zero Battery Drain (0% CPU):** The Android OS kernel tracks foreground app usage natively. No background polling loops or timers are used.
* **Automatic App Suspension on Limit:** The moment an app reaches its configured daily limit (e.g., 30 minutes for YouTube, 15 minutes for Instagram), Android OS notifies Test DPC, which **instantly freezes the app**.
* **12:00 AM Midnight Auto-Reset:** An `AlarmManager` daily alarm automatically resets usage counters and unsuspends all apps every night at midnight (12:00 AM).

---

### 💻 5. Windows 10/11 PC Protection Architecture
* **Google & Bing SafeSearch Hardening (`hosts` File):** Maps Google & Bing to Strict SafeSearch IP (`216.239.38.120`).
* **Total Notorious Domain Lockdown:** Maps X (`x.com`, `twitter.com`), Reddit (`reddit.com`, `redd.it`), Tumblr (`tumblr.com`), Telegram Web (`telegram.org`), and Web Proxies (`croxyproxy.com`, `proxysite.com`, `hide.me`, `blockaway.net`) to `0.0.0.0` in system `hosts` file and adds wildcard entries to Chrome & Edge `URLBlocklist`. *(Discord is allowed)*.
* **Chrome & Edge Direct Connection Lockdown:**
  * **`ProxyMode` = `"direct"`**: Forces direct connections in Chrome and Edge, preventing proxy/VPN extensions from overriding browser network settings.
  * **`ForceYouTubeRestrict` = `0`**: Disables YouTube Restricted Mode so YouTube comments and live chats load 100% normally.
* **Windows VPN & Proxy Lock (`DISALLOW_CONFIG_VPN`):** Disables adding new VPN connections or proxy servers in Windows Settings, and disables the Windows `RasMan` Remote Access VPN service.

---

## 📁 Repository Structure

```text
├── testdpc_source/                     # Merged Single App Source Code (Test DPC + Guard + Blocker + Timers + AI Auditor)
│   └── app/src/main/java/com/afwsamples/testdpc/
│       ├── PolicyManagementActivity.java # Main DPC Activity with USB ADB Lock Guard
│       ├── SetupManagementActivity.java  # Setup Activity with USB ADB Lock Guard
│       ├── AiAppAuditor.java             # Real-Time Gemini AI App Security Auditor Engine
│       ├── BrowserBlocker.java           # Pure Dynamic Auto Non-Chrome Browser Blocker Engine
│       ├── NotoriousAppBlocker.java      # Notorious App Auto-Freezer Engine
│       ├── ChromePolicyManager.java      # Default Chrome Managed Policies & Proxy Direct Engine
│       ├── AppTimerManager.java          # Impulse-Proof App Usage Limits & System Observers Engine
│       ├── AppTimerReceiver.java         # Daily Limit Exceeded & Midnight Reset Receiver
│       └── policy/
│           └── PolicyManagementFragment.java # Test DPC UI with Auto-Blocker Switch, AI Auditor & App Timers Dialog
├── Lock_TestDPC.bat                    # 1-Click USB ADB Script: Lock Test DPC & Protection
├── Unlock_TestDPC.bat                  # 1-Click USB ADB Script: Unlock Test DPC for Maintenance
├── Enable_Windows_Protection.bat       # 1-Click Administrator Script: Apply Windows Protection Policies
├── build_merged_dpc.ps1                # PowerShell Script to compile Test DPC APK
├── enable_windows_protection.ps1       # Windows PowerShell Script (SafeSearch, Cloudflare Family DNS & Proxy Direct)
├── enable_windows_protection.reg       # Windows Registry (.reg) Policy Export
└── README.md                           # Comprehensive Documentation
```

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
