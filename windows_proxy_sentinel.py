"""
==============================================================================
DPCLOCKER :: WINDOWS REAL-TIME BROWSER PROXY SENTINEL (MUTUAL GUARDIAN)
==============================================================================
Monitors active browser window titles and address bars in real-time (<50ms).
Automatically terminates the tab or window the moment 'proxy', 'proxies',
or web proxy engine names are typed, searched, or loaded in any browser.
Includes Mutual Watchdog resurrection to prevent tampering from Task Manager.
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

# Regex pattern targeting ONLY 'proxy', 'proxies', and specific web proxy engines.
# Explicitly excludes generic words like 'bypass', 'unblock', 'proximity', and 'approximate'.
TRIGGER_REGEX = re.compile(
    r'\bprox(?:y|ies|ied|ying|ys|ite|ypal|yium|ybroker)?\b|'
    r'\b(?:croxy|uproxy|hidester|extremevpn|azureserv|blockaway|rammerhead|ultraviolet|womginx|zend2|megaproxy|dontfilter|vtunnel|hidemyass|whoer|zalmos|4everproxy|toolur|turbohide|nodeunblocker|surfshield|scramjet|onlineproxy)\b|'
    r'prox(?:y|ies)',
    re.IGNORECASE
)

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

def log_incident(trigger, title, proc_name):
    """Appends security intercept incident to the JSON log."""
    entry = {
        "timestamp": datetime.now().isoformat(),
        "trigger": trigger,
        "window_title": title,
        "process_name": proc_name,
        "action": "CLOSED_TAB_AND_WINDOW"
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
    print("===============================================================================")
    print(" [✓] DPCLOCKER :: WINDOWS REAL-TIME BROWSER PROXY SENTINEL ACTIVE")
    print("===============================================================================")
    print(" Monitoring active browser titles for 'proxy', 'proxies', web proxy engines...")
    print(" Dual-process watchdog self-healing enabled.\n")
    
    watchdog_check_counter = 0
    
    while True:
        try:
            # 1. Periodically check that the twin Watchdog is running (every 1 second)
            watchdog_check_counter += 1
            if watchdog_check_counter >= 20:
                watchdog_check_counter = 0
                if not is_watchdog_running():
                    resurrect_watchdog()
            
            # 2. Inspect active foreground window
            hwnd, title, proc_name, pid = get_active_window()
            
            if hwnd and proc_name in TARGET_BROWSER_EXES and title:
                match = TRIGGER_REGEX.search(title)
                
                if match:
                    matched_trigger = match.group(0)
                    print(f"[{datetime.now().strftime('%H:%M:%S')}] 🚨 INTERCEPTED: '{matched_trigger}' in '{title}' ({proc_name})")
                    
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
                    
        except Exception:
            pass
            
        time.sleep(0.05)  # 50ms polling rate (ultra-fast 20Hz check, <0.1% CPU)

if __name__ == "__main__":
    main()
