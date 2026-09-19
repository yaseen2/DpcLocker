"""
==============================================================================
DPCLOCKER :: WINDOWS REAL-TIME BROWSER PROXY, REMOTE BROWSER & ONLINE VPN SENTINEL
==============================================================================
Monitors active browser window titles and address bars in real-time (<50ms).
Automatically terminates the tab or window the moment 'proxy', 'proxies',
'online remote browser', 'virtual browser', or 'online vpn' / web vpn proxies are typed,
searched, or loaded in any browser.
Includes Mutual Watchdog resurrection and Dynamic Hot-Reloading from disk.
==============================================================================
"""

import ctypes
from ctypes import wintypes
import time
import os
import sys
import re
import json
import subprocess
import threading
import psutil
from datetime import datetime

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32
advapi32 = ctypes.windll.advapi32

# Explicit 64-bit Win32 API signatures for DACL and Process management
kernel32.GetCurrentProcess.restype = wintypes.HANDLE
kernel32.GetCurrentProcessId.restype = wintypes.DWORD
kernel32.OpenProcess.restype = wintypes.HANDLE
kernel32.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
kernel32.CloseHandle.restype = wintypes.BOOL
kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
kernel32.DuplicateHandle.restype = wintypes.BOOL
kernel32.DuplicateHandle.argtypes = [
    wintypes.HANDLE, wintypes.HANDLE, wintypes.HANDLE,
    ctypes.POINTER(wintypes.HANDLE), wintypes.DWORD, wintypes.BOOL, wintypes.DWORD
]

advapi32.ConvertStringSecurityDescriptorToSecurityDescriptorW.restype = wintypes.BOOL
advapi32.ConvertStringSecurityDescriptorToSecurityDescriptorW.argtypes = [
    wintypes.LPCWSTR, wintypes.DWORD, ctypes.POINTER(wintypes.LPVOID), ctypes.POINTER(wintypes.ULONG)
]
advapi32.SetKernelObjectSecurity.restype = wintypes.BOOL
advapi32.SetKernelObjectSecurity.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.LPVOID]

# Win32 Mutex & Wait API signatures for zero-latency single-instance and liveness checks
SYNCHRONIZE = 0x00100000
INFINITE = 0xFFFFFFFF
ERROR_ALREADY_EXISTS = 183

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SENTINEL_SCRIPT = os.path.join(BASE_DIR, "windows_proxy_sentinel.py")
WATCHDOG_SCRIPT = os.path.join(BASE_DIR, "windows_sentinel_watchdog.py")
GUARD_SCRIPT = os.path.join(BASE_DIR, "windows_sentinel_guard.py")

def _get_user_suffix():
    try:
        u = os.environ.get("USERNAME") or ""
        clean = re.sub(r'[^a-zA-Z0-9_]', '_', u.lower())
        return f"_{clean}" if clean else ""
    except Exception:
        return ""

_USER_SUFFIX = _get_user_suffix()

SENTINEL_MUTEX_NAME = f"Local\\DpcLockerSentinelMutex_Sec{_USER_SUFFIX}"
WATCHDOG_MUTEX_NAME = f"Local\\DpcLockerWatchdogMutex_Sec{_USER_SUFFIX}"
GUARD_MUTEX_NAME = f"Local\\DpcLockerGuardMutex_Sec{_USER_SUFFIX}"

kernel32.CreateMutexW.restype = wintypes.HANDLE
kernel32.CreateMutexW.argtypes = [wintypes.LPVOID, wintypes.BOOL, wintypes.LPCWSTR]
kernel32.OpenMutexW.restype = wintypes.HANDLE
kernel32.OpenMutexW.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.LPCWSTR]
kernel32.WaitForSingleObject.restype = wintypes.DWORD
kernel32.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]

# Virtual-Key codes
VK_CONTROL = 0x11
VK_W = 0x57
KEYEVENTF_KEYUP = 0x0002
WM_CLOSE = 0x0010

SENTINEL_PID_FILE = os.path.join(BASE_DIR, f".sentinel{_USER_SUFFIX}.pid")
WATCHDOG_PID_FILE = os.path.join(BASE_DIR, f".watchdog{_USER_SUFFIX}.pid")
GUARD_PID_FILE = os.path.join(BASE_DIR, f".guard{_USER_SUFFIX}.pid")
STOP_SIGNAL_FILE = os.path.join(BASE_DIR, f".sentinel_stop_signal{_USER_SUFFIX}")
GLOBAL_STOP_SIGNAL_FILE = os.path.join(BASE_DIR, ".sentinel_stop_signal")

PYTHONW_PATH = sys.executable.replace("python.exe", "pythonw.exe")
if not os.path.exists(PYTHONW_PATH):
    for _cand in [
        r"C:\Users\ThinkPad\AppData\Local\Programs\Python\Python311\pythonw.exe",
        r"C:\Program Files\Python311\pythonw.exe",
        r"C:\Windows\pyw.exe",
        "pythonw.exe"
    ]:
        if os.path.exists(_cand):
            PYTHONW_PATH = _cand
            break

def is_stop_signaled():
    return os.path.exists(STOP_SIGNAL_FILE) or os.path.exists(GLOBAL_STOP_SIGNAL_FILE)

# Ensure valid stdout/stderr under pythonw.exe
if sys.stdout is None:
    sys.stdout = open(os.devnull, 'w', encoding='utf-8')
if sys.stderr is None:
    sys.stderr = open(os.devnull, 'w', encoding='utf-8')

import traceback
def handle_exception(exc_type, exc_value, exc_tb):
    try:
        with open(os.path.join(BASE_DIR, "sentinel_error.log"), "a", encoding="utf-8") as f:
            f.write(f"[{datetime.now().isoformat()}] Uncaught exception:\n")
            traceback.print_exception(exc_type, exc_value, exc_tb, file=f)
    except Exception:
        pass
sys.excepthook = handle_exception

# Supported browser executable process names (case-insensitive)
TARGET_BROWSER_EXES = {
    "chrome.exe",
    "msedge.exe",
    "brave.exe",
    "firefox.exe",
    "opera.exe",
    "vivaldi.exe",
    "thorium.exe",
    "arc.exe",
    "librewolf.exe",
    "waterfox.exe",
    "duckduckgo.exe"
}

# Specific non-'vpn' named VPN, tunnel, and proxy client executables
TARGET_TUNNEL_EXES = {
    "wireguard.exe",
    "warp-svc.exe",
    "cloudflarewarp.exe",
    "psiphon.exe",
    "psiphon3.exe",
    "shadowsocks.exe",
    "shadowsocksr.exe",
    "v2ray.exe",
    "xray.exe",
    "clash.exe",
    "clash-verge.exe",
    "tailscale.exe",
    "tailscaled.exe",
    "zerotier-one_x64.exe",
    "zerotier-one.exe",
    "outline.exe",
    "tor.exe"
}

# Standalone Desktop Music Streaming & Player Clients
TARGET_MUSIC_EXES = {
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
    "winamp.exe"
}

# Whitelisted processes that should NEVER be killed even if editing a file containing 'vpn'
WHITELISTED_TITLE_EXES = {
    "code.exe",
    "notepad.exe",
    "notepad++.exe",
    "devenv.exe",
    "sublime_text.exe",
    "explorer.exe",
    "cmd.exe",
    "powershell.exe",
    "pwsh.exe",
    "python.exe",
    "pythonw.exe",
    "git.exe"
}

# Generic VPN window title pattern
VPN_TITLE_REGEX = re.compile(r'\bvpn(?:s)?\b', re.IGNORECASE)

def build_trigger_regex():
    """Compiles and returns the master regex for proxies, remote browsers, online VPN proxies, adult tabs, and music streaming platforms."""
    return re.compile(
        # 1. All Proxy Variations (including compound proxies like thepiratesproxy, croxyproxy, etc.)
        r'\b(?:[a-z0-9_-]+)?prox(?:y|ies|ied|ying|ys|ite|ypal|yium|ybroker)?\b|'
        
        # 2. Remote / Cloud / Virtual / Disposable / Web-based Browser bypass phrases
        r'\b(?:online|free|web|disposable|virtual|remote|cloud|temporary|sandbox|ephemeral|isolated)\s+(?:remote\s+)?browser(?:s)?\b|'
        r'\b(?:web-based|web\s+based)\s+browser(?:s)?\b|'
        r'\bbrowser\s+(?:in\s+(?:a\s+)?browser|online|remote|emulator|sandbox|isolation)\b|'
        r'\b(?:run|open)\s+(?:chrome|browser|firefox|edge)\s+online\b|'
        r'\bunblock(?:ed)?\s+browser(?:s)?\b|'
        
        # 3. Online VPN / Web VPN / Browser VPN / No-Download VPN Proxies
        r'\b(?:online|web|browser|cloud|free\s+online|no\s+download|in\s+browser|virtual)\s+vpn(?:s)?\b|'
        r'\bvpn\s+(?:online|in\s+(?:a\s+)?browser|without\s+download|no\s+download|website|web\s+proxy|proxy|unblocker)\b|'
        r'\b(?:onlinevpn|online\s*vpn|webvpn|freeonlinevpn)(?:\.app)?\b|'
        r'onlinevpn\.app|'
        
        # 4. Specific Web Proxy, Remote Browser & Online VPN Platforms / Engines
        r'\b(?:croxy|uproxy|hidester|extremevpn|azureserv|blockaway|rammerhead|ultraviolet|womginx|zend2|megaproxy|dontfilter|vtunnel|hidemyass|whoer|zalmos|4everproxy|toolur|turbohide|nodeunblocker|surfshield|scramjet|onlineproxy|onlinevpn)\b|'
        r'\b(?:browserling|neverinstall|hyperbeam|kasm|kasmweb|webvm|distrosea|onworks|squarex|sqrx)\b|'
        
        # 4b. Torrent, Piracy & Uncensored Download Indexers
        r'\b(?:pirate\s*bay|thepiratebay|pirateproxy|thepiratesproxy|piratesproxy|tpb(?:\.party)?|1337x|torrentgalaxy|limetorrents|nyaa\.si|yts\.(?:mx|am))\b|'
        r'\b(?:thepiratebay\.org|thepiratesproxy\.org|pirateproxy\.)\b|'
        
        # 5. Major Adult Content & Pornographic Tab Titles (Guards Edge, Chrome, and all browsers)
        r'\b(?:porn|porno|pornography|xxx|xhamster|pornhub|xvideos|xnxx|redtube|youporn|spankbang|tnaflix|eporner|chaturbate|stripchat|camsoda|bongacams|onlyfans|brazzers|naughtyamerica|realitykings|bangbros|faphouse|nhentai|rule34)\b|'
        r'\b(?:free\s+porn|sex\s+video(?:s)?|adult\s+video(?:s)?|nude\s+chat|cam\s+girls|live\s+sex)\b|'
        
        # 6. Music Streaming Platforms & Online Audio Players
        r'\b(?:youtube\s+music|music\.youtube\.com)\b|'
        r'\b(?:spotify|open\.spotify\.com|play\.spotify\.com)\b|'
        r'\b(?:soundcloud|soundcloud\.com)\b|'
        r'\b(?:apple\s+music|music\.apple\.com)\b|'
        r'\b(?:amazon\s+music|music\.amazon\.)\b|'
        r'\b(?:deezer|deezer\.com)\b|'
        r'\b(?:tidal|listen\.tidal\.com)\b|'
        r'(?:\bpandora\.com\b|[-–|:]\s*pandora\b|\bpandora\s*[-–|:]|\bpandora\s+(?:music|radio|streaming|station)\b)|'
        r'\b(?:gaana(?:\.com)?)\b|'
        r'\b(?:jiosaavn|saavn)\b|'
        r'\b(?:wynk(?:\s+music)?|wynk\.in)\b|'
        r'\b(?:hungama\s+music|hungama\.com)\b|'
        r'\b(?:bandcamp(?:\.com)?)\b|'
        r'\b(?:audiomack(?:\.com)?)\b|'
        r'\b(?:qobuz(?:\.com)?)\b|'
        r'\b(?:napster(?:\.com)?)\b|'
        r'\b(?:tunein(?:\.com)?)\b|'
        r'\b(?:iheart(?:radio)?|iheart\.com)\b|'
        r'\b(?:last\.fm|mixcloud)\b|'
        r'\b(?:music\s+streaming|stream\s+music|listen\s+to\s+music\s+online|online\s+music\s+player|free\s+music\s+online)\b|'
        
        # 7. Root exact stems
        r'prox(?:y|ies)',
        re.IGNORECASE
    )

TRIGGER_REGEX = build_trigger_regex()
LOG_FILE = os.path.join(BASE_DIR, "windows_security_log.json")

def get_process_name_from_pid(pid):
    """Retrieves the executable name for a given process ID."""
    PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
    h_proc = kernel32.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, False, pid)
    if not h_proc:
        return ""
    
    exe_name = ctypes.create_unicode_buffer(512)
    size = wintypes.DWORD(512)
    try:
        if kernel32.QueryFullProcessImageNameW(h_proc, 0, exe_name, ctypes.byref(size)):
            full_path = exe_name.value
            return os.path.basename(full_path).lower()
    finally:
        kernel32.CloseHandle(h_proc)
    return ""

def protect_process_from_termination():
    """
    Hardens the process Security Descriptor (DACL).
    Denies PROCESS_TERMINATE (0x0001) and PROCESS_SUSPEND_RESUME (0x0800)
    to Everyone / standard users so Task Manager and taskkill cannot terminate it.
    """
    try:
        DACL_SECURITY_INFORMATION = 0x00000004
        SDDL_REVISION_1 = 1
        WRITE_DAC = 0x00040000

        # Deny Terminate (0x1) & Suspend/Resume (0x800) to Everyone (WD) & Built-in Users (BU);
        # Allow Full Control (GA) to SYSTEM (SY); Allow read/query/vm_read (0x121411) to Everyone
        sddl = "D:(D;;0x0801;;;WD)(D;;0x0801;;;BU)(A;;GA;;;SY)(A;;0x121411;;;WD)"
        p_sd = wintypes.LPVOID()
        sd_size = wintypes.ULONG()

        if advapi32.ConvertStringSecurityDescriptorToSecurityDescriptorW(
            sddl, SDDL_REVISION_1, ctypes.byref(p_sd), ctypes.byref(sd_size)
        ):
            curr_proc = kernel32.GetCurrentProcess()
            real_handle = wintypes.HANDLE()
            if kernel32.DuplicateHandle(curr_proc, curr_proc, curr_proc, ctypes.byref(real_handle), WRITE_DAC, False, 0):
                ret = advapi32.SetKernelObjectSecurity(real_handle, DACL_SECURITY_INFORMATION, p_sd)
                kernel32.CloseHandle(real_handle)
                kernel32.LocalFree(p_sd)
                if ret:
                    print(f"[{datetime.now().strftime('%H:%M:%S')}] [+] Process DACL hardened: Immune to Task Manager & taskkill.")
                    return True
            else:
                kernel32.LocalFree(p_sd)
    except Exception as e:
        print(f"[-] DACL hardening notice: {e}")
    return False

def check_stop_signal():
    """Checks if an authorized stop was requested via Stop_Proxy_Sentinel."""
    if is_stop_signaled():
        try:
            if os.path.exists(SENTINEL_PID_FILE):
                os.remove(SENTINEL_PID_FILE)
            print(f"[{datetime.now().strftime('%H:%M:%S')}] Authorized stop signal received. Shutting down cleanly.")
        except Exception:
            pass
        sys.exit(0)

def get_window_class_name(hwnd):
    """Returns the Win32 window class name for a given hwnd."""
    if not hwnd:
        return ""
    buff = ctypes.create_unicode_buffer(256)
    user32.GetClassNameW(hwnd, buff, 256)
    return buff.value

def is_browser_window(proc_name, class_name):
    """Determines whether a window belongs to a web browser by process name or window class."""
    if not proc_name:
        return False
    if proc_name in TARGET_BROWSER_EXES:
        return True
    if class_name in ("Chrome_WidgetWin_1", "MozillaWindowClass"):
        return True
    return False

def get_active_window():
    """Returns (hwnd, window_title, process_name, class_name, pid) for the foreground window."""
    hwnd = user32.GetForegroundWindow()
    if not hwnd:
        return None, "", "", "", 0
    
    length = user32.GetWindowTextLengthW(hwnd)
    if length == 0:
        return hwnd, "", "", "", 0
    
    buff = ctypes.create_unicode_buffer(length + 1)
    user32.GetWindowTextW(hwnd, buff, length + 1)
    title = buff.value
    
    pid = wintypes.DWORD()
    user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
    proc_name = get_process_name_from_pid(pid.value)
    class_name = get_window_class_name(hwnd)
    
    return hwnd, title, proc_name, class_name, pid.value

def close_active_tab():
    """Simulates Ctrl+W to instantly close the active browser tab."""
    user32.keybd_event(VK_CONTROL, 0, 0, 0)
    user32.keybd_event(VK_W, 0, 0, 0)
    time.sleep(0.02)
    user32.keybd_event(VK_W, 0, KEYEVENTF_KEYUP, 0)
    user32.keybd_event(VK_CONTROL, 0, KEYEVENTF_KEYUP, 0)

def close_window(hwnd):
    """Sends WM_CLOSE message to the window."""
    user32.PostMessageW(hwnd, WM_CLOSE, 0, 0)

def is_mutex_held(mutex_name):
    """Checks if a named Win32 mutex is currently held by an active process."""
    h = kernel32.OpenMutexW(SYNCHRONIZE, False, mutex_name)
    if h:
        kernel32.CloseHandle(h)
        return True
    return False

def spawn_process(script_path):
    """Spawns a triad member script via pythonw completely detached."""
    try:
        subprocess.Popen(
            [PYTHONW_PATH, script_path],
            cwd=BASE_DIR,
            creationflags=subprocess.CREATE_NO_WINDOW | subprocess.DETACHED_PROCESS
        )
    except Exception as e:
        try:
            with open(os.path.join(BASE_DIR, "sentinel_error.log"), "a", encoding="utf-8") as f:
                f.write(f"[{datetime.now().isoformat()}] spawn_process failed for {script_path}: {e}\n")
        except Exception:
            pass

def monitor_peer(peer_name, script_path, pid_file, mutex_name):
    """
    Zero-latency kernel synchronization thread.
    Waits on peer process handle with WaitForSingleObject (0ms latency).
    Immediately resurrects peer upon termination.
    """
    while True:
        if is_stop_signaled():
            break

        peer_pid = None
        cand_pids = [pid_file]
        if not _USER_SUFFIX:
            cand_pids.append(os.path.join(BASE_DIR, os.path.basename(pid_file)))
        for cand_pid in cand_pids:
            if os.path.exists(cand_pid):
                try:
                    with open(cand_pid, "r", encoding="utf-8") as f:
                        content = f.read().strip()
                        if content.isdigit():
                            peer_pid = int(content)
                            break
                except Exception:
                    peer_pid = None

        h_proc = None
        if peer_pid and peer_pid != os.getpid():
            h_proc = kernel32.OpenProcess(SYNCHRONIZE, False, peer_pid)

        if h_proc:
            # Blocks in Windows NT kernel at 0.0% CPU until process terminates.
            # Unblocks in <1ms upon process death!
            kernel32.WaitForSingleObject(h_proc, INFINITE)
            kernel32.CloseHandle(h_proc)

            if is_stop_signaled():
                break

            # Instant resurrection
            spawn_process(script_path)
            time.sleep(0.08)
        else:
            # PID file was missing or invalid; check if peer is alive via Mutex
            if not is_mutex_held(mutex_name):
                if not is_stop_signaled():
                    spawn_process(script_path)
            time.sleep(0.15)

def is_vpn_process(proc_name):
    """Checks if the process name is a generic VPN executable or known tunnel."""
    if not proc_name:
        return False
    name_lower = proc_name.lower()
    if name_lower in WHITELISTED_TITLE_EXES:
        return False
    if "vpn" in name_lower:
        return True
    if name_lower in TARGET_TUNNEL_EXES:
        return True
    return False

def is_music_process(proc_name):
    """Checks if the process name is a known standalone music streaming/player executable."""
    if not proc_name:
        return False
    name_lower = proc_name.lower()
    if name_lower in WHITELISTED_TITLE_EXES:
        return False
    return name_lower in TARGET_MUSIC_EXES

def terminate_vpn_process(pid, proc_name, reason):
    """Forcefully terminates a VPN process and logs the incident."""
    try:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] [!] TERMINATING VPN APP: {proc_name} (PID: {pid}) - Reason: {reason}")
        subprocess.run(["taskkill", "/F", "/PID", str(pid), "/T"], capture_output=True)
        log_incident(reason, f"Terminated {proc_name} (PID: {pid})", proc_name)
    except Exception as e:
        print(f"[-] Error terminating {proc_name}: {e}")

def terminate_music_process(pid, proc_name, reason):
    """Forcefully terminates a music client process and logs the incident."""
    try:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] [!] TERMINATING MUSIC APP: {proc_name} (PID: {pid}) - Reason: {reason}")
        subprocess.run(["taskkill", "/F", "/PID", str(pid), "/T"], capture_output=True)
        log_incident(reason, f"Terminated {proc_name} (PID: {pid})", proc_name)
    except Exception as e:
        print(f"[-] Error terminating {proc_name}: {e}")

def scan_and_kill_vpn_processes():
    """Scans all running processes in Windows and terminates any VPN process."""
    try:
        for proc in psutil.process_iter(['pid', 'name']):
            try:
                pname = proc.info.get('name') or ''
                if is_vpn_process(pname):
                    terminate_vpn_process(proc.info['pid'], pname, "GENERIC_VPN_PROCESS_DETECTED")
            except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
                pass
    except Exception:
        pass

def scan_and_kill_music_processes():
    """Scans all running processes in Windows and terminates any desktop music player/streamer process."""
    try:
        for proc in psutil.process_iter(['pid', 'name']):
            try:
                pname = proc.info.get('name') or ''
                if is_music_process(pname):
                    terminate_music_process(proc.info['pid'], pname, "DESKTOP_MUSIC_CLIENT_DETECTED")
            except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
                pass
    except Exception:
        pass

def check_and_disable_vpn_adapters():
    """Periodically checks and disables any TAP/TUN/Wintun/WireGuard/VPN virtual adapter."""
    try:
        cmd = 'powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-NetAdapter -ErrorAction SilentlyContinue | Where-Object { ($_.InterfaceDescription -match \'TAP|TUN|Wintun|WireGuard|VPN|Virtual\' -or $_.Name -match \'TAP|TUN|Wintun|WireGuard|VPN\') -and $_.Status -ne \'Disabled\' } | ForEach-Object { Disable-NetAdapter -Name $_.Name -Confirm:$false ; Write-Output $_.Name }"'
        res = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        disabled = res.stdout.strip()
        if disabled:
            print(f"[{datetime.now().strftime('%H:%M:%S')}] [!] DISABLED VIRTUAL VPN ADAPTER(S): {disabled}")
            log_incident("DISABLED_VIRTUAL_ADAPTER", disabled, "NetAdapterSentry")
    except Exception:
        pass

def log_incident(trigger, title, proc_name):
    """Appends security intercept incident to the JSON log."""
    entry = {
        "timestamp": datetime.now().isoformat(),
        "trigger": trigger,
        "window_title": title,
        "process_name": proc_name,
        "action": "TERMINATED_VPN_OR_CLOSED_TAB"
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

def background_system_guard():
    while True:
        try:
            if is_stop_signaled():
                break
            scan_and_kill_vpn_processes()
            scan_and_kill_music_processes()
            check_and_disable_vpn_adapters()
        except Exception:
            pass
        time.sleep(2.5)

def main():
    global TRIGGER_REGEX
    try:
        kernel32.FreeConsole()
    except Exception:
        pass

    # 0. Single instance check via Win32 Named Mutex
    h_sentinel_mutex = kernel32.CreateMutexW(None, False, SENTINEL_MUTEX_NAME)
    if kernel32.GetLastError() == ERROR_ALREADY_EXISTS:
        if h_sentinel_mutex:
            kernel32.CloseHandle(h_sentinel_mutex)
        sys.exit(0)

    print("===============================================================================")
    print(" [+] DPCLOCKER :: WINDOWS PROXY, VPN & REMOTE BROWSER SENTINEL ACTIVE")
    print("===============================================================================")
    print(" Monitoring active browser titles for proxies, online remote browsers, and web VPNs...")
    print(" Active generic VPN Process & Window Killer + NetAdapter Sentry ENABLED.")
    print(" Win32 DACL anti-termination and dual-process watchdog self-healing enabled.\n")
    
    # 1. Apply Win32 DACL hardening to make process immune to Task Manager / taskkill
    protect_process_from_termination()

    # 2. Record Sentinel PID
    try:
        with open(SENTINEL_PID_FILE, "w", encoding="utf-8") as f:
            f.write(str(os.getpid()))
    except Exception:
        pass

    # 3. Launch asynchronous background worker for heavy VPN and adapter checks
    guard_thread = threading.Thread(target=background_system_guard, daemon=True)
    guard_thread.start()

    # 4. Start zero-latency kernel monitor threads for Watchdog Alpha and Guard Beta
    t_watchdog = threading.Thread(
        target=monitor_peer,
        args=("Watchdog", WATCHDOG_SCRIPT, WATCHDOG_PID_FILE, WATCHDOG_MUTEX_NAME),
        daemon=True
    )
    t_guard = threading.Thread(
        target=monitor_peer,
        args=("Guard", GUARD_SCRIPT, GUARD_PID_FILE, GUARD_MUTEX_NAME),
        daemon=True
    )
    t_watchdog.start()
    t_guard.start()

    reload_check_counter = 0
    last_mtime = os.path.getmtime(__file__)
    
    while True:
        try:
            # Check for authorized stop signal
            check_stop_signal()

            # 1. Check for file modification to dynamically hot-reload without restart
            reload_check_counter += 1
            if reload_check_counter >= 40:
                reload_check_counter = 0
                current_mtime = os.path.getmtime(__file__)
                if current_mtime != last_mtime:
                    last_mtime = current_mtime
                    TRIGGER_REGEX = build_trigger_regex()
            
            # 2. Inspect active foreground window
            hwnd, title, proc_name, class_name, pid = get_active_window()
            
            # Check A: Active window belongs to generic VPN process
            if hwnd and is_vpn_process(proc_name):
                terminate_vpn_process(pid, proc_name, "ACTIVE_VPN_WINDOW_PROCESS")
                close_window(hwnd)
                time.sleep(0.2)
                continue

            # Check A2: Active window belongs to desktop music client
            if hwnd and is_music_process(proc_name):
                terminate_music_process(pid, proc_name, "ACTIVE_MUSIC_WINDOW_PROCESS")
                close_window(hwnd)
                time.sleep(0.2)
                continue

            # Check B: Active window title matches generic 'VPN' in non-browser/non-IDE app
            if hwnd and proc_name and proc_name not in WHITELISTED_TITLE_EXES and not is_browser_window(proc_name, class_name):
                if title and VPN_TITLE_REGEX.search(title):
                    print(f"[{datetime.now().strftime('%H:%M:%S')}] [!] GENERIC VPN WINDOW DETECTED: '{title}' ({proc_name})")
                    terminate_vpn_process(pid, proc_name, f"GENERIC_VPN_WINDOW_TITLE: {title}")
                    close_window(hwnd)
                    time.sleep(0.2)
                    continue

            # Check C: Web Browser Tab Proxy / Remote Browser / Online VPN check
            if hwnd and is_browser_window(proc_name, class_name) and title:
                match = TRIGGER_REGEX.search(title)
                if match:
                    matched_trigger = match.group(0)
                    print(f"[{datetime.now().strftime('%H:%M:%S')}] [!] INTERCEPTED: '{matched_trigger}' in '{title}' ({proc_name})")
                    
                    # Instantly close tab with Ctrl+W
                    close_active_tab()
                    
                    # Log incident
                    log_incident(matched_trigger, title, proc_name)
                    
                    # Double-check: if still open after 100ms, close window
                    time.sleep(0.10)
                    new_hwnd, new_title, _, _, _ = get_active_window()
                    if new_hwnd == hwnd and TRIGGER_REGEX.search(new_title):
                        close_window(hwnd)
                        
                    time.sleep(0.2)
                    
        except Exception:
            time.sleep(0.5)
            
        time.sleep(0.05)  # 50ms polling rate (ultra-fast 20Hz check, <0.1% CPU)

if __name__ == "__main__":
    main()
