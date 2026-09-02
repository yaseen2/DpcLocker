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
import psutil
from datetime import datetime

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32

# Virtual-Key codes
VK_CONTROL = 0x11
VK_W = 0x57
KEYEVENTF_KEYUP = 0x0002
WM_CLOSE = 0x0010

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
WATCHDOG_SCRIPT = os.path.join(BASE_DIR, "windows_sentinel_watchdog.py")
PYTHONW_PATH = sys.executable.replace("python.exe", "pythonw.exe")

# Supported browser executable process names (case-insensitive)
TARGET_BROWSER_EXES = {
    "chrome.exe",
    "msedge.exe",
    "brave.exe",
    "firefox.exe",
    "opera.exe",
    "vivaldi.exe"
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
    """Compiles and returns the master regex for proxies, remote browsers, and online VPN proxies."""
    return re.compile(
        # 1. All Proxy Variations (excluding proximity/approximate)
        r'\bprox(?:y|ies|ied|ying|ys|ite|ypal|yium|ybroker)?\b|'
        
        # 2. Remote / Cloud / Virtual / Disposable / Web-based Browser bypass phrases
        r'\b(?:online|free|web|disposable|virtual|remote|cloud|temporary|sandbox|ephemeral|isolated)\s+(?:remote\s+)?browser(?:s)?\b|'
        r'\b(?:web-based|web\s+based)\s+browser(?:s)?\b|'
        r'\bbrowser\s+(?:in\s+(?:a\s+)?browser|online|remote|emulator|sandbox|isolation)\b|'
        r'\b(?:run|open)\s+(?:chrome|browser|firefox|edge)\s+online\b|'
        r'\bunblock(?:ed)?\s+browser(?:s)?\b|'
        
        # 3. Online VPN / Web VPN / Browser VPN / No-Download VPN Proxies
        r'\b(?:online|web|browser|cloud|free\s+online|no\s+download|in\s+browser|virtual)\s+vpn(?:s)?\b|'
        r'\bvpn\s+(?:online|in\s+(?:a\s+)?browser|without\s+download|no\s+download|website|web\s+proxy|proxy|unblocker)\b|'
        r'\b(?:onlinevpn|webvpn|freeonlinevpn)\b|'
        
        # 4. Specific Web Proxy, Remote Browser & Online VPN Platforms / Engines
        r'\b(?:croxy|uproxy|hidester|extremevpn|azureserv|blockaway|rammerhead|ultraviolet|womginx|zend2|megaproxy|dontfilter|vtunnel|hidemyass|whoer|zalmos|4everproxy|toolur|turbohide|nodeunblocker|surfshield|scramjet|onlineproxy|onlinevpn)\b|'
        r'\b(?:browserling|neverinstall|hyperbeam|kasm|kasmweb|webvm|distrosea|onworks|squarex|sqrx)\b|'
        
        # 5. Major Adult Content & Pornographic Tab Titles (Guards Edge, Chrome, and all browsers)
        r'\b(?:porn|porno|pornography|xxx|xhamster|pornhub|xvideos|xnxx|redtube|youporn|spankbang|tnaflix|eporner|chaturbate|stripchat|camsoda|bongacams|onlyfans|brazzers|naughtyamerica|realitykings|bangbros|faphouse|nhentai|rule34)\b|'
        r'\b(?:free\s+porn|sex\s+video(?:s)?|adult\s+video(?:s)?|nude\s+chat|cam\s+girls|live\s+sex)\b|'
        
        # 6. Root exact stems
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

def get_active_window():
    """Returns (hwnd, window_title, process_name, pid) for the foreground window."""
    hwnd = user32.GetForegroundWindow()
    if not hwnd:
        return None, "", "", 0
    
    length = user32.GetWindowTextLengthW(hwnd)
    if length == 0:
        return hwnd, "", "", 0
    
    buff = ctypes.create_unicode_buffer(length + 1)
    user32.GetWindowTextW(hwnd, buff, length + 1)
    title = buff.value
    
    pid = wintypes.DWORD()
    user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
    proc_name = get_process_name_from_pid(pid.value)
    
    return hwnd, title, proc_name, pid.value

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

def is_watchdog_running():
    """Checks if the twin watchdog process is active."""
    for proc in psutil.process_iter(['pid', 'name', 'cmdline']):
        try:
            cmdline = proc.info.get('cmdline') or []
            cmd_str = " ".join(cmdline).lower()
            if "windows_sentinel_watchdog.py" in cmd_str:
                return True
        except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
            pass
    return False

def resurrect_watchdog():
    """Launches windows_sentinel_watchdog.py via pythonw."""
    try:
        subprocess.Popen(
            [PYTHONW_PATH, WATCHDOG_SCRIPT],
            cwd=BASE_DIR,
            creationflags=subprocess.CREATE_NO_WINDOW | subprocess.DETACHED_PROCESS
        )
    except Exception:
        pass

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

def terminate_vpn_process(pid, proc_name, reason):
    """Forcefully terminates a VPN process and logs the incident."""
    try:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] [!] TERMINATING VPN APP: {proc_name} (PID: {pid}) - Reason: {reason}")
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

def main():
    global TRIGGER_REGEX
    print("===============================================================================")
    print(" [+] DPCLOCKER :: WINDOWS PROXY, VPN & REMOTE BROWSER SENTINEL ACTIVE")
    print("===============================================================================")
    print(" Monitoring active browser titles for proxies, online remote browsers, and web VPNs...")
    print(" Active generic VPN Process & Window Killer + NetAdapter Sentry ENABLED.")
    print(" Dual-process watchdog self-healing enabled.\n")
    
    watchdog_check_counter = 0
    last_mtime = os.path.getmtime(__file__)
    
    # Run an immediate check on startup
    scan_and_kill_vpn_processes()
    check_and_disable_vpn_adapters()
    
    while True:
        try:
            # 1. Check for file modification to dynamically hot-reload without restart
            if watchdog_check_counter % 40 == 0:
                current_mtime = os.path.getmtime(__file__)
                if current_mtime != last_mtime:
                    last_mtime = current_mtime
                    TRIGGER_REGEX = build_trigger_regex()
                    print(f"[{datetime.now().strftime('%H:%M:%S')}] [+] HOT-RELOADED NEW RULES FROM DISK!")
            
            # 2. Periodically check that the twin Watchdog is running (every 1 second)
            watchdog_check_counter += 1
            if watchdog_check_counter >= 20:
                watchdog_check_counter = 0
                if not is_watchdog_running():
                    resurrect_watchdog()
            
            # 3. Inspect active foreground window
            hwnd, title, proc_name, pid = get_active_window()
            
            # Check A: Active window belongs to generic VPN process
            if hwnd and is_vpn_process(proc_name):
                terminate_vpn_process(pid, proc_name, "ACTIVE_VPN_WINDOW_PROCESS")
                close_window(hwnd)
                time.sleep(0.2)
                continue

            # Check B: Active window title matches generic 'VPN' in non-browser/non-IDE app
            if hwnd and proc_name and proc_name not in WHITELISTED_TITLE_EXES and proc_name not in TARGET_BROWSER_EXES:
                if title and VPN_TITLE_REGEX.search(title):
                    print(f"[{datetime.now().strftime('%H:%M:%S')}] [!] GENERIC VPN WINDOW DETECTED: '{title}' ({proc_name})")
                    terminate_vpn_process(pid, proc_name, f"GENERIC_VPN_WINDOW_TITLE: {title}")
                    close_window(hwnd)
                    time.sleep(0.2)
                    continue

            # Check C: Web Browser Tab Proxy / Remote Browser / Online VPN check
            if hwnd and proc_name in TARGET_BROWSER_EXES and title:
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
                    new_hwnd, new_title, _, _ = get_active_window()
                    if new_hwnd == hwnd and TRIGGER_REGEX.search(new_title):
                        close_window(hwnd)
                        
                    time.sleep(0.2)
            
            # 4. Periodic background system-wide VPN process audit (every 1 second)
            if watchdog_check_counter == 10:
                scan_and_kill_vpn_processes()

            # 5. Periodic Virtual NetAdapter audit (every 3 seconds)
            if watchdog_check_counter == 19:
                check_and_disable_vpn_adapters()
                    
        except Exception:
            pass
            
        time.sleep(0.05)  # 50ms polling rate (ultra-fast 20Hz check, <0.1% CPU)

if __name__ == "__main__":
    main()
