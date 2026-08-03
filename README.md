# DPC Locker 🔒

**Zero-Trust, Impulse-Proof Android Device Owner Lock & Cross-Platform Protection System**

DPC Locker is a lightweight, 100% offline Android protection system paired with Windows Registry policies. It locks down Android enterprise device management (`Test DPC`) so that policy changes, un-provisioning, or disabling browser safety restrictions **can only be performed when physically connected to a PC via USB ADB cable**.

---

## 🎯 Key Features & Architecture

* **Impulse-Proof Local Enforcement:** Prevents modifying Test DPC or turning off protection on the phone UI.
* **Laser-Targeted Protection:** 
  * Blocks launching Test DPC (`com.afwsamples.testdpc`).
  * Blocks accessing the specific **"Use DPC Locker Protection"** toggle screen in Android Settings.
  * **Leaves 100% of standard phone settings (Wi-Fi, Bluetooth, Display, Sound, Battery, Storage, etc.) completely accessible.**
* **USB ADB State Control:** Lock state is toggled via system settings (`dpclocker_enabled`) using 1-click batch scripts over USB ADB.
* **100% Offline & Private:** Zero internet permissions declared; 0% data tracking.
* **Pixel OS Crash-Resistant:** Uses a persistent `ForegroundService` notification and battery optimization exemptions to prevent Android 14/15 Phantom Process Killer from freezing the service.
* **Cross-Platform Protection:** Disables Incognito and InPrivate browsing on Windows (Chrome & Edge) and Android (Chrome), and locks VPN/Proxy creation in Windows Settings.

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
├── enable_windows_protection.ps1       # Windows PowerShell Script (Adult Content, SafeSearch & VPN Lock)
├── enable_windows_protection.reg       # Windows Registry (.reg) File (Chrome, Edge & Windows Network Policies)
└── README.md                           # Documentation
```

---

## 🛠️ Complete Setup Guide

### 1. Windows Setup (Adult Content, Incognito & VPN Locked)

Run `enable_windows_protection.ps1` in PowerShell as Administrator or double-click `enable_windows_protection.reg`.

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

### 4. Configuring Chrome & Security Restrictions in Test DPC

Connect phone via USB and run `Unlock_TestDPC.bat` to open Test DPC:

1. **Disable Chrome Incognito:**
   * **Manage App Restrictions** -> **Google Chrome** -> `IncognitoModeAvailability` = `1`
2. **Force Strict SafeSearch (No Unblur Option):**
   * `ForceGoogleSafeSearch` = `true`
   * `SafeSearchMode` = `1`
3. **Chrome Built-in Adult Content Filtering:**
   * `SafeSitesFilterBehavior` = `1`
4. **Enforce User Restrictions:**
   * `DISALLOW_UNINSTALL_APPS` = `true` *(Prevents uninstalling DpcLocker on phone UI)*
   * `DISALLOW_APPS_CONTROL` = `true` *(Prevents clearing app data or modifying app settings)*
   * `DISALLOW_CONFIG_PRIVATE_DNS` = `true` *(Locks Private DNS filter)*

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
