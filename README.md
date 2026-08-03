# DPC Locker 🔒

**Zero-Trust, Impulse-Proof Android Device Owner Lock & Cross-Platform Self-Control System**

DPC Locker is a lightweight, 100% offline Android protection system paired with Windows Registry policies. It locks down Android enterprise device management (`Test DPC`) and browser safety settings so that policy modifications, un-provisioning, or disabling protection **can only be performed when physically connected to a PC via USB ADB cable**.

---

## 🎯 Key Features & Architecture

### 📱 Android Protection Architecture
* **Impulse-Proof Local Enforcement:** Prevents opening Test DPC or turning off protection on the phone UI.
* **Laser-Targeted Protection:** 
  * Blocks launching Test DPC (`com.afwsamples.testdpc`).
  * Blocks accessing the specific **"Use DPC Locker Protection"** toggle screen in Android Settings.
  * **Leaves 100% of standard phone settings (Wi-Fi, Bluetooth, Display, Sound, Battery, Storage, Apps, etc.) completely accessible.**
* **USB ADB State Control:** Lock state is toggled via system settings (`dpclocker_enabled`) using 1-click batch scripts over USB ADB.
* **100% Offline & Private:** Zero internet permissions declared; 0% data tracking.
* **Pixel OS Crash-Resistant:** Uses a persistent `ForegroundService` notification and battery optimization exemptions to prevent Android 14/15 Phantom Process Killer from freezing the service.
* **Browser Adult Content & SafeSearch Enforcement:** Disables Incognito in Chrome, forces Strict Google SafeSearch (no unblur option), and enables Chrome's built-in SafeSites adult content filter.

### 💻 Windows 10 PC Protection Architecture
* **System-Wide Adult Content DNS Filter:** Sets Wi-Fi & Ethernet DNS to CleanBrowsing Family Filter (`185.228.168.168` / `185.228.169.168`) to block adult domains across all Windows apps.
* **Google & Bing SafeSearch Hardening (`hosts` File):** Maps Google & Bing to Strict SafeSearch IP (`216.239.38.120`).
* **Chrome & Edge Registry Policies:** Disables Incognito in Chrome and InPrivate in Edge, forces Strict SafeSearch, and enables Chrome SafeSites adult filter.
* **Windows VPN & Proxy Lock:** Disables adding new VPN connections or proxy servers in Windows Settings, and disables the Windows `RasMan` Remote Access VPN service.
* **Productivity Friendly:** Normal Chrome and Edge extensions remain 100% allowed so daily productivity tools continue working uninterrupted.

---

## 📁 Repository Structure

```text
├── DpcLocker/                          # Custom Android App Source Code
│   ├── AndroidManifest.xml             # App Manifest (minSdkVersion=24, targetSdkVersion=34)
│   ├── res/
│   │   └── xml/
│   │       └── accessibility_service_config.xml # Accessibility listener config
│   └── src/com/custom/dpclocker/
│       ├── MainActivity.java           # Launcher Activity
│       └── DpcLockerService.java       # Accessibility & Foreground Service
├── Lock_TestDPC.bat                    # 1-Click USB ADB Script: Lock Test DPC & Toggle Screen
├── Unlock_TestDPC.bat                  # 1-Click USB ADB Script: Unlock Test DPC for Maintenance
├── build_dpclocker.ps1                 # Standalone PowerShell Build Script (AAPT2 + javac + D8 + apksigner)
├── enable_windows_protection.ps1       # Windows PowerShell Script (Lean Adult Content, SafeSearch & VPN Lock)
├── enable_windows_protection.reg       # Windows Registry (.reg) File (Chrome, Edge & Windows Network Policies)
└── README.md                           # Documentation
```

---

## 🛠️ Complete Setup Guide

### 1. Windows Setup (Adult Content, Incognito & VPN Locked)

Open PowerShell as Administrator and run `enable_windows_protection.ps1` (or double-click `enable_windows_protection.reg`).

Applied Policies:
* **CleanBrowsing Family DNS:** Sets system DNS to `185.228.168.168` and `185.228.169.168` (blocks adult domains system-wide).
* **System Hosts Overrides:** Maps Google & Bing to Strict SafeSearch IP (`216.239.38.120`).
* **Windows VPN & Proxy Lock:** Disables adding new VPN connections or proxy servers in Windows Settings.
* **Disables Windows RasMan Service:** Prevents starting the Windows Remote Access VPN service.
* **Chrome & Edge Registry Policies:**
  * `IncognitoModeAvailability` = `1` (Disables Incognito)
  * `InPrivateModeAvailability` = `1` (Disables InPrivate)
  * `ForceGoogleSafeSearch` = `1` (Forces Strict SafeSearch)
  * `SafeSitesFilterBehavior` = `1` (Enforces Chrome adult site filter)

---

### 2. Android Device Owner Provisioning (Test DPC)

1. Install **Test DPC** (`com.afwsamples.testdpc`) on your Android phone.
2. Remove all secondary user profiles and Google accounts from phone settings temporarily during provisioning.
3. Connect phone to PC via USB ADB and set Test DPC as Device Owner:
   ```cmd
   adb shell dpm set-device-owner com.afwsamples.testdpc/.DeviceAdminReceiver
   ```
4. Re-add your Google accounts.

---

### 3. Deploying DPC Locker App

1. Rebuild or install `DpcLocker.apk` on your phone:
   ```cmd
   powershell -ExecutionPolicy Bypass -File "build_dpclocker.ps1"
   adb install -r "DpcLocker\build\DpcLocker.apk"
   ```
2. Whitelist `DpcLocker` from battery optimization and grant restricted settings permission:
   ```cmd
   adb shell dumpsys deviceidle whitelist +com.custom.dpclocker
   adb shell appops set com.custom.dpclocker ACCESS_RESTRICTED_SETTINGS allow
   ```
3. Enable DPC Locker Accessibility Service via ADB:
   ```cmd
   adb shell settings put secure enabled_accessibility_services com.custom.dpclocker/com.custom.dpclocker.DpcLockerService
   adb shell settings put secure accessibility_enabled 1
   ```

---

### 4. Key Test DPC Policies & User Restrictions

Connect phone via USB and run `Unlock_TestDPC.bat` to open Test DPC:

#### ⚙️ Managed Configurations (App Restrictions)
* **Chrome Incognito:** `IncognitoModeAvailability` = `1` *(Disables Incognito Mode)*
* **Strict Google SafeSearch:** `ForceGoogleSafeSearch` = `true` & `SafeSearchMode` = `1` *(Removes explicit images & unblur toggle)*
* **Chrome Adult Site Filtering:** `SafeSitesFilterBehavior` = `1` *(Enforces Chrome's built-in SafeSites adult content filter)*

#### 🔒 Critical User Restrictions (In Test DPC > User Restrictions)
* **`Disallow uninstall apps` (`DISALLOW_UNINSTALL_APPS`):**  
  *Grays out and disables the "Uninstall" button for all applications on the phone UI (prevents uninstalling `DpcLocker`).*
* **`Disallow apps control` (`DISALLOW_APPS_CONTROL`):**  
  *Prevents clearing app data, modifying app permissions, or force-stopping apps in Android Settings.*
* **`Disallow install apps` (`DISALLOW_INSTALL_APPS`):**  
  *Blocks installing any new applications on the phone UI.*
* **`Disallow install from unknown sources` (`DISALLOW_INSTALL_UNKNOWN_SOURCES`):**  
  *Blocks installing APK files from outside the Google Play Store for the current user profile.*
* **`Disallow install from unknown sources globally` (`DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY`):**  
  *Enforces system-wide blocking of APK side-loading across all user profiles on the device.*
* **`Block Installation of Apps`:**  
  *Allows blacklisting specific package names (e.g. specific secondary browsers or VPN apps) from ever being installed on the device.*
* **`Suspend Apps`:**  
  *Instantly freezes and hides specific target apps on the phone UI without uninstalling them.*
* **`Disallow config private DNS` (`DISALLOW_CONFIG_PRIVATE_DNS`):**  
  *Locks system Private DNS settings so the CleanBrowsing adult filter cannot be changed.*

---

### 5. Private DNS System-Wide Adult Filter

On your phone, go to **Settings > Network & Internet > Private DNS**:
* Hostname: `family-filter-dns.cleanbrowsing.org` (or `family.cloudflare-dns.com`)

---

## ⚡ Daily USB ADB Operation

* **To Lock Everything (Default State):**  
  Double-click `Lock_TestDPC.bat`. Opening Test DPC or the DPC Locker toggle screen will instantly kick back to the Home Screen.
* **To Maintenance / Update Policies:**  
  Plug into PC via USB cable and double-click `Unlock_TestDPC.bat`. Make your changes in Test DPC, then run `Lock_TestDPC.bat` when done.

---

## 🔨 Building DPC Locker from Source

DPC Locker requires no heavy IDEs to build. You can build it using raw Android SDK build-tools via PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File "build_dpclocker.ps1"
```

---

## 📄 License

Open-source under the MIT License. Designed for personal self-control, digital wellness, and enterprise policy enforcement.
