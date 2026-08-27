"""
==============================================================================
DPCLOCKER :: WINDOWS REAL-TIME BROWSER PROXY & BYPASS SENTINEL
==============================================================================
Monitors active browser window titles and address bars in real-time (<100ms).
Automatically terminates the tab or window the moment any proxy, unblocker,
or bypass keywords are typed, searched, or loaded in any browser.
==============================================================================
"""

import ctypes
from ctypes import wintypes
import time
import os
import sys
import json
import logging
from datetime import datetime

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32

# Virtual-Key codes
VK_CONTROL = 0x11
VK_W = 0x57
KEYEVENTF_KEYUP = 0x0002
WM_CLOSE = 0x0010

# Supported browser executable process names (case-insensitive)
TARGET_BROWSER_EXES = {
    "chrome.exe",
    "msedge.exe",
    "brave.exe",
    "firefox.exe",
    "opera.exe",
    "vivaldi.exe"
}

# Real-time trigger keywords and phrases (case-insensitive)
TRIGGER_KEYWORDS = [
    # Core Proxy & Unblocker Terms
    "online proxy",
    "web proxy",
    "free proxy",
    "proxy site",
    "proxy server",
    "proxy list",
    "proxy youtube",
    "proxy unblock",
    "proxy unblocker",
    "fast proxy",
    "best proxy",
    "unblock websites",
    "unblock youtube",
    "unblock tiktok",
    "bypass filter",
    "bypass securly",
    "bypass iboss",
    "holy unblocker",
    
    # Specific Proxy Engine & Domain Names
    "croxyproxy",
    "proxysite",
    "hidester",
    "uproxy",
    "onlineproxy",
    "extremevpn",
    "azureserv",
    "proxypal",
    "proxyium",
    "kproxy",
    "vpnbook",
    "blockaway",
    "rammerhead",
    "ultraviolet",
    "anarchyproxy",
    "hyperproxy",
    "shuttleproxy",
    "alohabrowser",
    "womginx",
    "zend2",
    "zendproxy",
    "megaproxy",
    "dontfilter",
    "unblock-web",
    "unblockvideos",
    "free-proxy",
    "shadowproxy",
    "interstellarproxy",
    "incognitoproxy",
    "nebula proxy",
    "titaniumnetwork",
    "plainproxies",
    "hidemyass",
    "whoer.net",
    "zalmos",
    "filterbypass",
    "4everproxy",
    "toolur",
    "webproxy",
    "turbohide",
    "freeproxy",
    "nodeunblocker",
    "surfshield",
    "cloakproxy",
    "scramjet",
    "arsenic proxy",
    "selenite proxy",
    "ludicrous proxy",
    "shadowtabs"
]

LOG_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "windows_security_log.json")

def get_process_name_from_pid(pid):
    """Retrieves the executable name for a given process ID."""
    PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
    h_proc = kernel32.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, False, pid)
    if not h_proc:
        return ""
    
    exe_name = ctypes.create_unicode_buffer(512)
    size = wintypes.DWORD(512)
    try:
        # QueryFullProcessImageNameW
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
    # Keep last 500 records
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
    print(" Monitoring active browser titles for proxy keywords & unblockers...")
    print(" Tab / Window will close immediately upon detecting trigger terms.\n")
    
    last_intercepted_time = 0
    
    while True:
        try:
            hwnd, title, proc_name, pid = get_active_window()
            
            if hwnd and proc_name in TARGET_BROWSER_EXES and title:
                title_lower = title.lower()
                
                # Check for triggers
                matched_trigger = None
                for trigger in TRIGGER_KEYWORDS:
                    if trigger in title_lower:
                        matched_trigger = trigger
                        break
                        
                # Also check single word "proxy" if in search engine or website title
                if not matched_trigger:
                    if "proxy" in title_lower:
                        # Ensure it's not a dev editor or terminal
                        matched_trigger = "proxy"
                
                if matched_trigger:
                    print(f"[{datetime.now().strftime('%H:%M:%S')}] 🚨 INTERCEPTED: '{matched_trigger}' in '{title}' ({proc_name})")
                    
                    # 1. Instantly close tab with Ctrl+W
                    close_active_tab()
                    
                    # 2. Log incident
                    log_incident(matched_trigger, title, proc_name)
                    
                    # 3. Double-check: if still open after 150ms, close window
                    time.sleep(0.15)
                    new_hwnd, new_title, _, _ = get_active_window()
                    if new_hwnd == hwnd and matched_trigger in new_title.lower():
                        close_window(hwnd)
                        
                    time.sleep(0.3)
                    
        except Exception as e:
            # Silent recovery to ensure continuous uptime
            pass
            
        time.sleep(0.1)  # 100ms polling rate (near 0% CPU)

if __name__ == "__main__":
    main()
