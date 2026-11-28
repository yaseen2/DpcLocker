"""
==============================================================================
DPCLOCKER :: WINDOWS ELEVATED SYSTEM ENFORCER (SESSION 0 / NT AUTHORITY\SYSTEM)
==============================================================================
Runs as a high-privilege Windows Scheduled Task under NT AUTHORITY\SYSTEM.
Responsibilities:
  1. Zero-False-Positive Unauthorized Browser & VPN Detection via PE Metadata
     (VS_VERSION_INFO: CompanyName, ProductName, OriginalFilename) so renamed
     or portable browsers (e.g. brave.exe renamed to notes.exe) and UAC-elevated
     installers (BraveBrowserSetup*.exe, BraveUpdate.exe) are terminated
     instantly, while Chrome, Edge, and ALL legitimate apps remain 100% safe.
  2. Zero-PowerShell VPN Adapter Guard using psutil.net_if_addrs() targeting
     explicit VPN drivers ONLY (never Hyper-V, WSL2, Docker, VirtualBox, or
     Wi-Fi Direct virtual adapters).
  3. Periodic Self-Healing of Kernel IFEO blocks, Chrome/Edge/Brave HKLM
     Enterprise Policies (CleanBrowsing Adult DoH, locked Google SafeSearch,
     ExtensionSettings blocking proxy/vpnProvider permissions, TorDisabled),
     and CleanBrowsing Adult IPv4 + IPv6 DNS.
==============================================================================
"""

import ctypes
from ctypes import wintypes
import json
import os
import re
import subprocess
import sys
import time
from datetime import datetime

try:
    import winreg
except ImportError:
    winreg = None

try:
    import psutil
except ImportError:
    psutil = None

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
LOG_FILE = os.path.join(BASE_DIR, "windows_security_log.json")
STOP_SIGNAL_FILE = os.path.join(BASE_DIR, ".enforcer_stop_signal")
GLOBAL_STOP_SIGNAL_FILE = os.path.join(BASE_DIR, ".sentinel_stop_signal")
ENFORCER_PID_FILE = os.path.join(BASE_DIR, ".system_enforcer.pid")
ENFORCER_MUTEX_NAME = "Global\\DpcLockerSystemEnforcerMutex_v2"

IFEO_ROOT = r"SOFTWARE\Microsoft\Windows NT\CurrentVersion\Image File Execution Options"
IFEO_STUB = r"C:\Windows\System32\systray.exe"
CLEANBROWSING_DOH = "https://doh.cleanbrowsing.org/doh/adult-filter/"
CLEANBROWSING_IPV4 = ("185.228.168.10", "185.228.169.11")
CLEANBROWSING_IPV6 = ("2a0d:2a00:1::1", "2a0d:2a00:2::1")
GOOGLE_SAFESEARCH_URL = "https://www.google.com/search?q={searchTerms}&safe=active&ssui=on"
EXT_SETTINGS_JSON = '{"*":{"blocked_permissions":["proxy","vpnProvider"]}}'

# Exact unauthorized browser / installer executable names (lowercase)
BLOCKED_BROWSER_EXES = {
    "brave.exe",
    "braveupdate.exe",
    "braveupdatesetup.exe",
    "bravebrowsersetup.exe",
    "bravebrowsersetup-brv010.exe",
    "bravebrowserstandalonesetup.exe",
    "bravebrowserstandalonesilentsetup.exe",
    "bravebrowsernightlysetup.exe",
    "bravebrowserbetasetup.exe",
    "opera.exe",
    "opera_gx.exe",
    "operagx.exe",
    "opera_autoupdate.exe",
    "operasetup.exe",
    "operagxsetup.exe",
    "firefox.exe",
    "firefox installer.exe",
    "waterfox.exe",
    "librewolf.exe",
    "floorp.exe",
    "zen.exe",
    "palemoon.exe",
    "basilisk.exe",
    "seamonkey.exe",
    "mullvadbrowser.exe",
    "vivaldi.exe",
    "arc.exe",
    "thorium.exe",
    "comet.exe",
    "duckduckgo.exe",
    "tor.exe",
    "torbrowser.exe",
    "yandex.exe",
    "ucbrowser.exe",
    "aloha.exe",
    "maxthon.exe",
    "dragon.exe",
    "cromite.exe",
}

# Exact installer filename regexes (catches versioned downloads like BraveBrowserSetup-XYZ123.exe)
BLOCKED_INSTALLER_REGEX = re.compile(
    r"^(?:"
    r"bravebrowsersetup.*|"
    r"braveupdate.*|"
    r"operasetup.*|"
    r"operagxsetup.*|"
    r"firefox(?:[\s._-]*installer|[\s._-]*setup).*|"
    r"torbrowser[\s._-]*install.*|"
    r"vivaldi[\s._-]*\d.*|"
    r"waterfox[\s._-]*setup.*|"
    r"librewolf[\s._-]*setup.*|"
    r"mullvad[\s._-]*browser.*|"
    r"protonvpn[\s._-]*setup.*|"
    r"nordvpn[\s._-]*setup.*|"
    r"windscribe[\s._-]*\d.*|"
    r"psiphon3?.*"
    r")\.exe$",
    re.IGNORECASE,
)

# Exact VPN & Tunnel client executable names (lowercase)
BLOCKED_VPN_EXES = {
    "skyvpn.exe",
    "protonvpn.exe",
    "protonvpnservice.exe",
    "nordvpn.exe",
    "nordvpn-service.exe",
    "windscribe.exe",
    "windscribeservice.exe",
    "expressvpn.exe",
    "expressvpnd.exe",
    "surfshark.exe",
    "psiphon.exe",
    "psiphon3.exe",
    "wireguard.exe",
    "warp-svc.exe",
    "cloudflarewarp.exe",
    "openvpn.exe",
    "openvpn-gui.exe",
    "clash.exe",
    "clash-verge.exe",
    "v2ray.exe",
    "xray.exe",
    "shadowsocks.exe",
    "shadowsocksr.exe",
    "ultrasurf.exe",
    "lantern.exe",
    "hotspotshield.exe",
    "cyberghost.exe",
    "tunnelbear.exe",
    "mullvad-vpn.exe",
    "mullvad-daemon.exe",
    "outline.exe",
    "tailscale.exe",
    "tailscaled.exe",
    "zerotier-one.exe",
    "zerotier-one_x64.exe",
}

# Standalone desktop music streaming clients (lowercase)
BLOCKED_MUSIC_EXES = {
    "spotify.exe",
    "spotifymigrator.exe",
    "applemusic.exe",
    "amazon music.exe",
    "deezer.exe",
    "tidal.exe",
    "pandora.exe",
    "qobuz.exe",
    "musicbee.exe",
    "foobar2000.exe",
    "aimp.exe",
    "winamp.exe",
}

# Explicit safe / managed process names that must NEVER be terminated
SAFE_EXEMPT_EXES = {
    "chrome.exe",
    "msedge.exe",
    "msedgewebview2.exe",
    "code.exe",
    "antigravity.exe",
    "antigravity ide.exe",
    "agy.exe",
    "explorer.exe",
    "svchost.exe",
    "system",
    "registry",
    "smss.exe",
    "csrss.exe",
    "wininit.exe",
    "services.exe",
    "lsass.exe",
    "dwm.exe",
    "taskmgr.exe",
    "python.exe",
    "pythonw.exe",
    "powershell.exe",
    "pwsh.exe",
    "cmd.exe",
    "conhost.exe",
    "windowsterminal.exe",
    "wps.exe",
    "pdf24.exe",
    "winword.exe",
    "excel.exe",
    "powerpnt.exe",
    "acrobat.exe",
    "studio64.exe",
    "idea64.exe",
    "git.exe",
    "node.exe",
    "java.exe",
    "javaw.exe",
    "adb.exe",
    "fdm.exe",
    "epicpen.exe",
    "feem.exe",
    "browser_broker.exe",
    "searchapp.exe",
    "searchhost.exe",
    "applicationframehost.exe",
    "systemsettings.exe",
}

# Explicit safe directory prefixes (lowercase) - Windows system & trusted IDE/productivity tools
SAFE_DIR_HINTS = (
    "\\windows\\system32\\",
    "\\windows\\syswow64\\",
    "\\windows\\immersivecontrolpanel\\",
    "\\windows\\systemapps\\",
    "\\program files\\google\\chrome\\",
    "\\program files (x86)\\google\\chrome\\",
    "\\program files\\microsoft\\edge\\",
    "\\program files (x86)\\microsoft\\edge\\",
    "\\program files (x86)\\microsoft\\edgewebview\\",
    "\\programs\\antigravity\\",
    "\\programs\\antigravity ide\\",
    "\\programs\\microsoft vs code\\",
    "\\kingsoft\\wps office\\",
)

# Explicit VPN adapter driver/interface patterns (NEVER matches generic "Virtual" / Hyper-V / WSL / Docker!)
VPN_ADAPTER_REGEX = re.compile(
    r"(?:tap-?windows|wintun|wireguard|openvpn|nordlynx|protonvpn|skyvpn|psiphon|"
    r"cloudflare\s*warp|hotspot\s*shield|expressvpn|surfshark|windscribe|mullvad|tailscale|zerotier)",
    re.IGNORECASE,
)

# Cache for PE Version Info: {(exe_path_lower, mtime, size): dict}
_PE_CACHE = {}


def get_pe_version_info(exe_path):
    """
    Reads Windows PE VS_VERSION_INFO metadata (CompanyName, ProductName,
    OriginalFilename, FileDescription) using Win32 version.dll.
    Returns a lowercase-keyed dict; empty dict if unavailable.
    """
    if not exe_path or not os.path.isfile(exe_path):
        return {}
    try:
        st = os.stat(exe_path)
        cache_key = (exe_path.lower(), int(st.st_mtime), st.st_size)
        if cache_key in _PE_CACHE:
            return _PE_CACHE[cache_key]
    except OSError:
        return {}

    info = {}
    try:
        version_dll = ctypes.windll.version
        size = version_dll.GetFileVersionInfoSizeW(exe_path, None)
        if not size:
            _PE_CACHE[cache_key] = info
            return info

        buf = ctypes.create_string_buffer(size)
        if not version_dll.GetFileVersionInfoW(exe_path, 0, size, buf):
            _PE_CACHE[cache_key] = info
            return info

        # Query translation table for language/codepage
        lptr = ctypes.c_void_p()
        lsize = wintypes.UINT()
        lang_codepages = [("0409", "04b0"), ("0409", "04e4"), ("0000", "04b0")]
        if version_dll.VerQueryValueW(buf, r"\VarFileInfo\Translation", ctypes.byref(lptr), ctypes.byref(lsize)):
            if lsize.value >= 4:
                arr = ctypes.cast(lptr, ctypes.POINTER(wintypes.WORD))
                lang_codepages.insert(0, (f"{arr[0]:04x}", f"{arr[1]:04x}"))

        fields = ("CompanyName", "ProductName", "OriginalFilename", "FileDescription")
        for field in fields:
            for lang, cp in lang_codepages:
                subblock = f"\\StringFileInfo\\{lang}{cp}\\{field}"
                vptr = ctypes.c_void_p()
                vlen = wintypes.UINT()
                if version_dll.VerQueryValueW(buf, subblock, ctypes.byref(vptr), ctypes.byref(vlen)):
                    if vlen.value > 0 and vptr.value:
                        val = ctypes.wstring_at(vptr.value).strip()
                        if val:
                            info[field] = val
                            break
    except Exception:
        pass

    if len(_PE_CACHE) > 2000:
        _PE_CACHE.clear()
    _PE_CACHE[cache_key] = info
    return info


def classify_process(proc_name, exe_path="", pe_info=None):
    """
    Classifies a process strictly with ZERO false positives on good apps.
    Returns (category, reason) or (None, None) if allowed.
    Categories: 'UNAUTHORIZED_BROWSER', 'VPN_CLIENT', 'MUSIC_CLIENT'.
    """
    pname = (proc_name or "").strip().lower()
    epath = (exe_path or "").strip().lower()
    pe = pe_info if pe_info is not None else (get_pe_version_info(exe_path) if exe_path else {})

    orig_name = (pe.get("OriginalFilename") or "").strip().lower()
    company = (pe.get("CompanyName") or "").strip().lower()
    product = (pe.get("ProductName") or "").strip().lower()
    desc = (pe.get("FileDescription") or "").strip().lower()

    # 1. Explicit safe directory / Chrome & Edge verification
    # Note: Even if someone renames brave.exe to chrome.exe and runs it from Downloads,
    # PE metadata (orig_name == 'brave.exe' or company == 'brave software') will STILL catch it!
    is_genuine_chrome_or_edge = (
        (pname == "chrome.exe" and ("google" in company or "\\google\\chrome\\" in epath or not pe))
        or (pname in ("msedge.exe", "msedgewebview2.exe") and ("microsoft" in company or "\\microsoft\\edge" in epath or not pe))
    )
    if is_genuine_chrome_or_edge and orig_name not in BLOCKED_BROWSER_EXES and "brave" not in company and "opera" not in company:
        return None, None

    # 2. Check PE OriginalFilename & Company/Product FIRST (catches renamed browsers/VPNs!)
    if orig_name in BLOCKED_BROWSER_EXES:
        return "UNAUTHORIZED_BROWSER", f"PE OriginalFilename matched blocked browser: {orig_name}"

    if orig_name in BLOCKED_VPN_EXES:
        return "VPN_CLIENT", f"PE OriginalFilename matched blocked VPN: {orig_name}"

    if orig_name in BLOCKED_MUSIC_EXES:
        return "MUSIC_CLIENT", f"PE OriginalFilename matched blocked music client: {orig_name}"

    # Positive PE publisher/product fingerprints for unauthorized browsers (even if renamed)
    if (
        "brave software" in company
        or "bravesoftware" in company
        or "brave browser" in product
        or "brave update" in product
        or "bravesoftware update" in product
        or "opera software" in company
        or ("opera" in product and "browser" in (product + " " + desc))
        or ("mozilla" in company and "firefox" in product)
        or "vivaldi technologies" in company
        or "tor project" in company
        or "tor browser" in product
        or "waterfox" in product
        or "librewolf" in product
        or ("duckduckgo" in company and "browser" in (product + " " + desc))
        or ("perplexity" in company and "comet" in product)
        or ("browser company" in company and "arc" in product)
        or "zen browser" in product
        or "floorp" in product
        or "mullvad browser" in product
        or ("yandex" in company and "browser" in (product + " " + desc))
    ):
        return "UNAUTHORIZED_BROWSER", f"PE metadata matched unauthorized browser ({company} / {product})"

    # Positive PE publisher/product fingerprints for VPN/tunnel software
    vpn_pe_markers = (
        "nordvpn", "protonvpn", "proton vpn", "expressvpn", "surfshark",
        "windscribe", "skyvpn", "psiphon", "hotspot shield", "cyberghost",
        "tunnelbear", "mullvad vpn", "openvpn", "wireguard", "cloudflare warp",
        "ultrasurf", "clash verge",
    )
    pe_blob = f"{company} {product} {desc}"
    for marker in vpn_pe_markers:
        if marker in pe_blob:
            return "VPN_CLIENT", f"PE metadata matched VPN client ({marker})"

    # 3. Exempt safe system & known good productivity executables (now that renamed PE checks passed)
    if pname in SAFE_EXEMPT_EXES:
        return None, None
    for hint in SAFE_DIR_HINTS:
        if hint in epath:
            return None, None

    # 4. Exact executable filename checks
    if pname in BLOCKED_BROWSER_EXES or BLOCKED_INSTALLER_REGEX.match(pname):
        return "UNAUTHORIZED_BROWSER", f"Blocked browser/installer executable: {pname}"

    if pname in BLOCKED_VPN_EXES:
        return "VPN_CLIENT", f"Blocked VPN/tunnel executable: {pname}"

    if pname in BLOCKED_MUSIC_EXES:
        return "MUSIC_CLIENT", f"Blocked desktop music client: {pname}"

    # 5. Path-based fallback for BraveSoftware / Opera / TorBrowser installation folders
    if (
        "\\bravesoftware\\" in epath
        or "\\programs\\opera\\" in epath
        or "\\mozilla firefox\\" in epath
        or "\\tor browser\\" in epath
        or "\\vivaldi\\application\\" in epath
    ):
        return "UNAUTHORIZED_BROWSER", f"Process executing from blocked browser directory: {epath}"

    return None, None


def log_incident(trigger, details, proc_name, action="TERMINATED_BY_SYSTEM_ENFORCER"):
    entry = {
        "timestamp": datetime.now().isoformat(),
        "trigger": trigger,
        "window_title": details,
        "process_name": proc_name,
        "action": action,
    }
    records = []
    if os.path.exists(LOG_FILE):
        try:
            with open(LOG_FILE, "r", encoding="utf-8") as f:
                records = json.load(f)
        except Exception:
            records = []
    records.append(entry)
    if len(records) > 500:
        records = records[-500:]
    try:
        with open(LOG_FILE, "w", encoding="utf-8") as f:
            json.dump(records, f, indent=2)
    except Exception:
        pass


def scan_and_enforce_processes():
    """Scans all running processes and terminates unauthorized browsers, VPNs, and music clients."""
    if not psutil:
        return
    my_pid = os.getpid()
    for proc in psutil.process_iter(["pid", "name", "exe"]):
        try:
            pid = proc.info.get("pid")
            if not pid or pid == my_pid or pid <= 4:
                continue
            pname = proc.info.get("name") or ""
            exe_path = proc.info.get("exe") or ""
            category, reason = classify_process(pname, exe_path)
            if category:
                try:
                    proc.kill()
                except Exception:
                    subprocess.run(
                        ["taskkill", "/F", "/PID", str(pid), "/T"],
                        capture_output=True,
                        creationflags=subprocess.CREATE_NO_WINDOW,
                    )
                log_incident(category, f"{reason} | Path={exe_path}", pname)
        except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
            pass
        except Exception:
            pass


def check_and_disable_vpn_adapters():
    """
    Zero-PowerShell check using psutil.net_if_addrs().
    Only disables adapters matching explicit VPN driver names (NEVER generic 'Virtual').
    """
    if not psutil:
        return
    try:
        stats = psutil.net_if_stats()
        for iface_name, st in stats.items():
            if not st.isup:
                continue
            # Never touch Wi-Fi, Ethernet, Loopback, WSL, Hyper-V, Docker, VirtualBox, VMware
            lower_name = iface_name.lower()
            if any(safe in lower_name for safe in ("wi-fi", "ethernet", "loopback", "vethernet", "wsl", "docker", "vmware", "virtualbox", "local area connection")):
                continue
            if VPN_ADAPTER_REGEX.search(iface_name):
                subprocess.run(
                    ["netsh", "interface", "set", "interface", f"name={iface_name}", "admin=disabled"],
                    capture_output=True,
                    creationflags=subprocess.CREATE_NO_WINDOW,
                )
                log_incident("DISABLED_VPN_ADAPTER", iface_name, "SystemEnforcer")
    except Exception:
        pass


def self_heal_ifeo_and_browser_policies():
    """Re-asserts kernel IFEO blocks and Chrome/Edge/Brave HKLM Enterprise Policies via winreg (0 subprocesses)."""
    if not winreg:
        return
    # 1. Re-assert IFEO keys
    all_ifeo = sorted(BLOCKED_BROWSER_EXES | BLOCKED_VPN_EXES)
    for exe in all_ifeo:
        try:
            with winreg.CreateKey(winreg.HKEY_LOCAL_MACHINE, f"{IFEO_ROOT}\\{exe}") as k:
                winreg.SetValueEx(k, "Debugger", 0, winreg.REG_SZ, IFEO_STUB)
        except PermissionError:
            return  # Running non-elevated; skip HKLM writes cleanly
        except Exception:
            pass

    # 2. Re-assert Chrome, Edge & Brave HKLM Policies
    browser_keys = [
        (r"SOFTWARE\Policies\Google\Chrome", "IncognitoModeAvailability", False, False),
        (r"SOFTWARE\Policies\Microsoft\Edge", "InPrivateModeAvailability", True, False),
        (r"SOFTWARE\Policies\BraveSoftware\Brave", "IncognitoModeAvailability", False, True),
    ]
    for subkey, incog_prop, is_edge, is_brave in browser_keys:
        try:
            with winreg.CreateKey(winreg.HKEY_LOCAL_MACHINE, subkey) as k:
                winreg.SetValueEx(k, incog_prop, 0, winreg.REG_DWORD, 1)
                winreg.SetValueEx(k, "BrowserGuestModeEnabled", 0, winreg.REG_DWORD, 0)
                winreg.SetValueEx(k, "BrowserAddPersonEnabled", 0, winreg.REG_DWORD, 0)
                winreg.SetValueEx(k, "ForceGoogleSafeSearch", 0, winreg.REG_DWORD, 1)
                winreg.SetValueEx(k, "SafeSitesFilterBehavior", 0, winreg.REG_DWORD, 1)
                winreg.SetValueEx(k, "ForceYouTubeRestrict", 0, winreg.REG_DWORD, 0)
                winreg.SetValueEx(k, "DnsOverHttpsMode", 0, winreg.REG_SZ, "secure")
                winreg.SetValueEx(k, "DnsOverHttpsTemplates", 0, winreg.REG_SZ, CLEANBROWSING_DOH)
                winreg.SetValueEx(k, "ProxyMode", 0, winreg.REG_SZ, "direct")
                winreg.SetValueEx(k, "DefaultSearchProviderEnabled", 0, winreg.REG_DWORD, 1)
                winreg.SetValueEx(k, "DefaultSearchProviderName", 0, winreg.REG_SZ, "Google")
                winreg.SetValueEx(k, "DefaultSearchProviderSearchURL", 0, winreg.REG_SZ, GOOGLE_SAFESEARCH_URL)
                winreg.SetValueEx(k, "ExtensionSettings", 0, winreg.REG_SZ, EXT_SETTINGS_JSON)
                if is_edge:
                    winreg.SetValueEx(k, "ForceBingSafeSearch", 0, winreg.REG_DWORD, 2)
                if is_brave:
                    winreg.SetValueEx(k, "TorDisabled", 0, winreg.REG_DWORD, 1)
                    winreg.SetValueEx(k, "BraveVPNDisabled", 0, winreg.REG_DWORD, 1)
                    winreg.SetValueEx(k, "BraveAIChatEnabled", 0, winreg.REG_DWORD, 0)
        except Exception:
            pass


def self_heal_dns():
    """Ensures Wi-Fi & Ethernet IPv4 + IPv6 DNS stay locked to CleanBrowsing Adult Filter."""
    for iface in ("Wi-Fi", "Ethernet"):
        try:
            out = subprocess.run(
                ["netsh", "interface", "ipv4", "show", "dnsservers", f"name={iface}"],
                capture_output=True,
                text=True,
                creationflags=subprocess.CREATE_NO_WINDOW,
            )
            if out.returncode == 0 and CLEANBROWSING_IPV4[0] not in (out.stdout or ""):
                subprocess.run(
                    ["netsh", "interface", "ipv4", "set", "dns", f"name={iface}", "static", CLEANBROWSING_IPV4[0], "primary", "validate=no"],
                    capture_output=True,
                    creationflags=subprocess.CREATE_NO_WINDOW,
                )
                subprocess.run(
                    ["netsh", "interface", "ipv4", "add", "dns", f"name={iface}", CLEANBROWSING_IPV4[1], "index=2", "validate=no"],
                    capture_output=True,
                    creationflags=subprocess.CREATE_NO_WINDOW,
                )
            out6 = subprocess.run(
                ["netsh", "interface", "ipv6", "show", "dnsservers", f"name={iface}"],
                capture_output=True,
                text=True,
                creationflags=subprocess.CREATE_NO_WINDOW,
            )
            if out6.returncode == 0 and CLEANBROWSING_IPV6[0] not in (out6.stdout or ""):
                subprocess.run(
                    ["netsh", "interface", "ipv6", "set", "dns", f"name={iface}", "static", CLEANBROWSING_IPV6[0], "primary", "validate=no"],
                    capture_output=True,
                    creationflags=subprocess.CREATE_NO_WINDOW,
                )
                subprocess.run(
                    ["netsh", "interface", "ipv6", "add", "dns", f"name={iface}", CLEANBROWSING_IPV6[1], "index=2", "validate=no"],
                    capture_output=True,
                    creationflags=subprocess.CREATE_NO_WINDOW,
                )
        except Exception:
            pass


def is_stop_signaled():
    return os.path.exists(STOP_SIGNAL_FILE) or os.path.exists(GLOBAL_STOP_SIGNAL_FILE)


def main():
    if sys.stdout is None:
        sys.stdout = open(os.devnull, "w", encoding="utf-8")
    if sys.stderr is None:
        sys.stderr = open(os.devnull, "w", encoding="utf-8")

    kernel32 = ctypes.windll.kernel32
    kernel32.CreateMutexW.restype = wintypes.HANDLE
    kernel32.CreateMutexW.argtypes = [wintypes.LPVOID, wintypes.BOOL, wintypes.LPCWSTR]
    h_mutex = kernel32.CreateMutexW(None, False, ENFORCER_MUTEX_NAME)
    if kernel32.GetLastError() == 183:  # ERROR_ALREADY_EXISTS
        if h_mutex:
            kernel32.CloseHandle(h_mutex)
        sys.exit(0)

    try:
        with open(ENFORCER_PID_FILE, "w", encoding="utf-8") as f:
            f.write(str(os.getpid()))
    except Exception:
        pass

    # Initial self-heal pass immediately on startup
    self_heal_ifeo_and_browser_policies()
    self_heal_dns()

    last_policy_heal = time.monotonic()
    last_dns_heal = time.monotonic()

    while True:
        if is_stop_signaled():
            try:
                if os.path.exists(ENFORCER_PID_FILE):
                    os.remove(ENFORCER_PID_FILE)
            except Exception:
                pass
            sys.exit(0)

        try:
            scan_and_enforce_processes()
            check_and_disable_vpn_adapters()

            now = time.monotonic()
            if now - last_policy_heal >= 30.0:
                self_heal_ifeo_and_browser_policies()
                last_policy_heal = now
            if now - last_dns_heal >= 60.0:
                self_heal_dns()
                last_dns_heal = now
        except Exception:
            pass

        time.sleep(1.0)


if __name__ == "__main__":
    main()
