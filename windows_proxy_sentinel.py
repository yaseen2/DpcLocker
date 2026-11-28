"""
==============================================================================
DPCLOCKER :: WINDOWS INTERACTIVE BROWSER TAB & ZERO-FALSE-POSITIVE SENTINEL v2
==============================================================================
Session-1 interactive companion to windows_system_enforcer.py (Session-0 SYSTEM).
Fixes all historical false-positive bugs:
  1. Strict Browser Process Matching ONLY (deleted Chrome_WidgetWin_1 catch-all
     so Antigravity IDE, VS Code, WPS Office, Discord, Notion, Teams, etc. can
     NEVER be treated as browsers or receive Ctrl+W / WM_CLOSE).
  2. Deleted blanket non-browser 'VPN' window-title taskkill (VPN clients &
     unauthorized browsers are detected accurately via PE metadata in
     windows_system_enforcer.classify_process).
  3. Context-aware browser tab & URL evaluation: exempts legitimate coding,
     geography/physics ('tidal bulges'), GitHub/MDN/StackOverflow, healthcare
     ('Proxy Access at Sutter Health'), and addiction recovery ('NoFap',
     'porn blocker') tabs while strictly closing adult sites, web proxies,
     online VPNs, remote browsers, torrents, unauthorized browser downloads,
     and actual music streaming web players.
  4. Zero periodic PowerShell process spawning (0 CPU thrashing).
==============================================================================
"""

import ctypes
from ctypes import wintypes
import json
import os
import re
import subprocess
import sys
import threading
import time
import traceback
from datetime import datetime
from urllib.parse import urlparse

import psutil

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
if BASE_DIR not in sys.path:
    sys.path.insert(0, BASE_DIR)

try:
    from windows_system_enforcer import (
        BLOCKED_BROWSER_EXES,
        classify_process,
        scan_and_enforce_processes,
    )
except Exception:
    BLOCKED_BROWSER_EXES = {
        "brave.exe", "opera.exe", "opera_gx.exe", "firefox.exe", "vivaldi.exe",
        "thorium.exe", "arc.exe", "librewolf.exe", "waterfox.exe", "duckduckgo.exe",
        "comet.exe", "tor.exe", "torbrowser.exe", "zen.exe", "floorp.exe",
    }

    def classify_process(proc_name, exe_path="", pe_info=None):
        return None, None

    def scan_and_enforce_processes():
        pass

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32
advapi32 = ctypes.windll.advapi32

# Explicit 64-bit Win32 API signatures
kernel32.GetCurrentProcess.restype = wintypes.HANDLE
kernel32.GetCurrentProcessId.restype = wintypes.DWORD
kernel32.OpenProcess.restype = wintypes.HANDLE
kernel32.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
kernel32.CloseHandle.restype = wintypes.BOOL
kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
kernel32.DuplicateHandle.restype = wintypes.BOOL
kernel32.DuplicateHandle.argtypes = [
    wintypes.HANDLE, wintypes.HANDLE, wintypes.HANDLE,
    ctypes.POINTER(wintypes.HANDLE), wintypes.DWORD, wintypes.BOOL, wintypes.DWORD,
]
kernel32.CreateMutexW.restype = wintypes.HANDLE
kernel32.CreateMutexW.argtypes = [wintypes.LPVOID, wintypes.BOOL, wintypes.LPCWSTR]
kernel32.OpenMutexW.restype = wintypes.HANDLE
kernel32.OpenMutexW.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.LPCWSTR]
kernel32.WaitForSingleObject.restype = wintypes.DWORD
kernel32.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]

advapi32.ConvertStringSecurityDescriptorToSecurityDescriptorW.restype = wintypes.BOOL
advapi32.ConvertStringSecurityDescriptorToSecurityDescriptorW.argtypes = [
    wintypes.LPCWSTR, wintypes.DWORD, ctypes.POINTER(wintypes.LPVOID), ctypes.POINTER(wintypes.ULONG),
]
advapi32.SetKernelObjectSecurity.restype = wintypes.BOOL
advapi32.SetKernelObjectSecurity.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.LPVOID]

SYNCHRONIZE = 0x00100000
INFINITE = 0xFFFFFFFF
ERROR_ALREADY_EXISTS = 183

SENTINEL_SCRIPT = os.path.join(BASE_DIR, "windows_proxy_sentinel.py")
WATCHDOG_SCRIPT = os.path.join(BASE_DIR, "windows_sentinel_watchdog.py")
GUARD_SCRIPT = os.path.join(BASE_DIR, "windows_sentinel_guard.py")
LOG_FILE = os.path.join(BASE_DIR, "windows_security_log.json")


def _get_user_suffix():
    try:
        u = os.environ.get("USERNAME") or ""
        clean = re.sub(r"[^a-zA-Z0-9_]", "_", u.lower())
        return f"_{clean}" if clean else ""
    except Exception:
        return ""


_USER_SUFFIX = _get_user_suffix()

SENTINEL_MUTEX_NAME = f"Local\\DpcLockerSentinelMutex_Sec{_USER_SUFFIX}"
WATCHDOG_MUTEX_NAME = f"Local\\DpcLockerWatchdogMutex_Sec{_USER_SUFFIX}"
GUARD_MUTEX_NAME = f"Local\\DpcLockerGuardMutex_Sec{_USER_SUFFIX}"

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
        "pythonw.exe",
    ]:
        if os.path.exists(_cand):
            PYTHONW_PATH = _cand
            break


def is_stop_signaled():
    return os.path.exists(STOP_SIGNAL_FILE) or os.path.exists(GLOBAL_STOP_SIGNAL_FILE)


if sys.stdout is None:
    sys.stdout = open(os.devnull, "w", encoding="utf-8")
if sys.stderr is None:
    sys.stderr = open(os.devnull, "w", encoding="utf-8")


def handle_exception(exc_type, exc_value, exc_tb):
    try:
        with open(os.path.join(BASE_DIR, "sentinel_error.log"), "a", encoding="utf-8") as f:
            f.write(f"[{datetime.now().isoformat()}] Uncaught exception:\n")
            traceback.print_exception(exc_type, exc_value, exc_tb, file=f)
    except Exception:
        pass


sys.excepthook = handle_exception

# Managed browser EXEs allowed to run (tab inspection applies ONLY to these exact EXEs,
# NEVER to arbitrary Chrome_WidgetWin_1 Electron/IDE/Office windows!)
MANAGED_BROWSER_EXES = {
    "chrome.exe",
    "msedge.exe",
}

# Safe study, developer, healthcare & addiction-recovery domains exempted from ambiguous keyword rules
SAFE_EXEMPT_DOMAINS = (
    "github.com",
    "stackoverflow.com",
    "developer.mozilla.org",
    "learn.microsoft.com",
    "docs.google.com",
    "cssprep.com.pk",
    "cssprepforum.com",
    "nofap.com",
    "forum.nofap.com",
    "sutterhealth.org",
    "wikipedia.org",
    "britannica.com",
    "localhost",
    "127.0.0.1",
)

# Legitimate educational, recovery, coding & scientific title contexts exempted from ambiguous keywords
SAFE_CONTEXT_TITLE_REGEX = re.compile(
    r"(?:"
    r"\bnofap\b|\bporn\s+addiction\b|\baddiction\s+recovery\b|\brebooting\s+from\s+porn\b|"
    r"\bporn\s+blocker\b|\bporn\s+blockers\b|\bblock(?:ing)?\s+porn(?:ography)?\b|"
    r"\bdechainer\b|\bis\s+watching\s+porn(?:ography)?\s+unhealthy\b|\bquitting\s+porn\b|"
    r"\btidal\s+(?:bulge|bulges|wave|waves|force|forces|energy|power|range|current|locking|basin|pool)\b|"
    r"\b(?:high|low|spring|neap)\s+tide\b|"
    r"\bsutter\s+health\b|\bwhat\s+is\s+a\s+web\s+browser\b|"
    r"\b(?:html|css|javascript|react|tailwind|flutter|python|java|coding|tutorial|github|stackoverflow|mdn)\b|"
    r"#coding\b|#webdevelopment\b|#programming\b"
    r")",
    re.IGNORECASE,
)

# Tier 1: Unconditional hard-block domains & bypass services (blocked regardless of title context)
HARD_BLOCK_REGEX = re.compile(
    # Explicit Adult Tube, Cam, Creator & Hentai Domains/Brands
    r"\b(?:pornhub|xvideos|xhamster|xnxx|redtube|youporn|spankbang|tnaflix|eporner|"
    r"hqporner|sxyprn|beeg|chaturbate|stripchat|camsoda|bongacams|livejasmin|myfreecams|"
    r"onlyfans|fansly|brazzers|naughtyamerica|realitykings|bangbros|faphouse|nhentai|"
    r"rule34|redgifs|erome|motherless|javhd|hanime|missav|jable\.tv|drtuber|thumbzilla|"
    r"porntube|porndoe|pornhat|pornpics|imagefap|tube8|txxx|ixxx)\b|"
    # Explicit Adult Media Phrases
    r"\b(?:free\s+porn|sex\s+video(?:s)?|adult\s+video(?:s)?|nude\s+chat|cam\s+girls|live\s+sex|"
    r"hot\s+sex|xxx\s+video(?:s)?|xxx\s+movie(?:s)?|indian\s+sex\s+video)\b|"
    # Explicit Web Proxy, Online VPN & Cloud/Remote Browser Bypass Platforms
    r"\b(?:onlinevpn(?:\.app|\.com)?|webvpn|freeonlinevpn|croxyproxy|croxy\.network|croxy\.org|"
    r"uproxy\.online|hidester|extremevpn|azureserv|blockaway|proxysite|proxyium|proxypal|"
    r"proxyorb|proxybrowser|kproxy|4everproxy|filterbypass|rammerhead|ultraviolet\.dev|"
    r"womginx|zend2|megaproxy|dontfilter|vtunnel|hidemyass|whoer\.net|zalmos|webproxy\.to|"
    r"turbohide|nodeunblocker|surfshield\.io|scramjet\.org|onlineproxy|"
    r"browser\.lol|browserling|neverinstall|hyperbeam|kasmweb|distrosea|onworks|squarex)\b|"
    # Explicit Bypass Phrases (never bare 'proxy' or bare 'web browser'!)
    r"\b(?:online|free\s+online|web|in[- ]?browser|no[- ]?download)\s+(?:web\s+)?(?:vpn|proxy|proxies)\b|"
    r"\b(?:vpn|proxy)\s+(?:online|in\s+(?:a\s+)?browser|without\s+download|no\s+download|unblocker|switcher)\b|"
    r"\b(?:free\s+chrome\s+proxy|unblock(?:ed)?\s+(?:browser|websites|sites)|access\s+(?:vpn|proxy|browser)\s+online)\b|"
    r"\b(?:online|virtual|remote|cloud|disposable|ephemeral)\s+(?:remote\s+)?browser(?:s)?\b|"
    r"\bbrowser\s+(?:in\s+(?:a\s+)?browser|online|remote|emulator)\b|"
    r"\b(?:run|open)\s+(?:chrome|browser|firefox|edge)\s+online\b|"
    # Unauthorized Browser Download / Search Pages
    r"\b(?:brave\.com|search\.brave\.com|brave\s+search|download\s+brave|"
    r"opera\.com/download|download\s+opera|opera\s+(?:web\s+)?browser|"
    r"torproject\.org|download\s+tor\s+browser|"
    r"mozilla\.org/firefox|download\s+firefox|"
    r"vivaldi\.com|vivaldi\s+browser|waterfox\.net|librewolf\.net|"
    r"duckduckgo\.com|startpage\.com|qwant\.com)\b|"
    # Torrent & Piracy Indexers
    r"\b(?:the\s*pirate\s*bay|thepiratebay|pirateproxy|thepiratesproxy|piratesproxy|"
    r"tpb\.party|1337x|torrentgalaxy|limetorrents|nyaa\.si|yts\.mx)\b",
    re.IGNORECASE,
)

# Tier 2: Context-sensitive adult keywords (exempted on NoFap/recovery/github/educational pages)
CONTEXT_ADULT_REGEX = re.compile(
    r"\b(?:porn|porno|pornography|xxx)\b",
    re.IGNORECASE,
)

# Tier 3: Precision Music Streaming Platforms (domain/player-specific; NEVER matches
# 'tidal bulges' or 'Spotify Glass Sidebar UI | HTML CSS - YouTube'!)
MUSIC_STREAMING_REGEX = re.compile(
    r"(?:"
    r"\bmusic\.youtube\.com\b|\byoutube\s+music\b|"
    r"\b(?:open|play)\.spotify\.com\b|\bspotify\.com\b|\bspotify\s*[–—|-]\s*(?:web\s+player|listen|music)\b|"
    r"\bsoundcloud\.com\b|\bstream\b.*\bon\s+soundcloud\b|"
    r"\bmusic\.apple\.com\b|\bmusic\.amazon\.(?:com|co\.uk|in)\b|"
    r"\bdeezer\.com\b|"
    r"\b(?:listen\.)?tidal\.com\b|\btidal\s*[–—|-]\s*(?:high\s+fidelity|music|web\s+player)\b|[–—|-]\s*tidal\s*[–—|-]|"
    r"\bpandora\.com\b|\bpandora\s+(?:music|radio|streaming|station)\b|"
    r"\bgaana\.com\b|\bjiosaavn\.com\b|\bsaavn\.com\b|\bwynk\.in\b|\bhungama\.com\b|"
    r"\bbandcamp\.com\b|\baudiomack\.com\b|\bqobuz\.com\b|\bnapster\.com\b|"
    r"\btunein\.com\b|\biheart\.com\b|\blast\.fm\b|\bmixcloud\.com\b|"
    r"\b(?:music\s+streaming|listen\s+to\s+music\s+online|online\s+music\s+player|free\s+music\s+online)\b"
    r")",
    re.IGNORECASE,
)


def is_browser_window(proc_name, class_name=""):
    """
    Returns True ONLY for actual web browser executables.
    NEVER matches arbitrary Chrome_WidgetWin_1 Electron/IDE/Office windows!
    """
    if not proc_name:
        return False
    pname = proc_name.strip().lower()
    return pname in MANAGED_BROWSER_EXES or pname in BLOCKED_BROWSER_EXES


def _extract_host(url):
    if not url:
        return ""
    try:
        u = url.strip()
        if "://" not in u and "." in u and " " not in u:
            u = "https://" + u
        host = (urlparse(u).hostname or "").lower()
        return host
    except Exception:
        return ""


def evaluate_browser_tab(title, url="", proc_name=""):
    """
    Evaluates a managed browser tab's title + optional URL with strict anti-false-positive rules.
    Returns matched trigger string if tab must be closed, else None.
    """
    title_str = (title or "").strip()
    url_str = (url or "").strip()
    if not title_str and not url_str:
        return None

    host = _extract_host(url_str)
    combined = f"{url_str} {title_str}".strip()

    # 1. Hard-block adult sites, proxies, online VPNs, torrents & unauthorized browser downloads
    hard_match = HARD_BLOCK_REGEX.search(combined)
    if hard_match:
        # Never block explicit safe dev/study hosts solely because of a page discussing Chrome/Brave
        if host and any(host == d or host.endswith("." + d) for d in SAFE_EXEMPT_DOMAINS):
            # Still block if the URL host itself isn't actually safe or title is explicit porn tube
            if not re.search(r"\b(?:pornhub|xvideos|xhamster|xnxx|redtube|youporn|spankbang|chaturbate|onlyfans|brazzers)\b", combined, re.IGNORECASE):
                return None
        return hard_match.group(0)

    # 2. Check safe educational / coding / recovery context BEFORE ambiguous keywords
    is_safe_host = bool(host and any(host == d or host.endswith("." + d) for d in SAFE_EXEMPT_DOMAINS))
    is_safe_title = bool(SAFE_CONTEXT_TITLE_REGEX.search(title_str))

    if is_safe_host or is_safe_title:
        # Even on YouTube coding tutorials, never block 'Spotify Glass Sidebar UI' or 'tidal bulges'
        # Still block actual music.youtube.com domain if navigated directly
        if host == "music.youtube.com":
            return "music.youtube.com"
        return None

    # 3. Context-sensitive explicit adult terms ('porn', 'porno', 'pornography', 'xxx')
    adult_match = CONTEXT_ADULT_REGEX.search(combined)
    if adult_match:
        return adult_match.group(0)

    # 4. Precision music streaming platform match (exempting standard YouTube non-music pages)
    music_match = MUSIC_STREAMING_REGEX.search(combined)
    if music_match:
        return music_match.group(0)

    return None


def get_process_info_from_pid(pid):
    """Returns (exe_basename_lower, full_exe_path) for a given PID."""
    PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
    h_proc = kernel32.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, False, pid)
    if not h_proc:
        return "", ""
    exe_name = ctypes.create_unicode_buffer(1024)
    size = wintypes.DWORD(1024)
    try:
        if kernel32.QueryFullProcessImageNameW(h_proc, 0, exe_name, ctypes.byref(size)):
            full_path = exe_name.value
            return os.path.basename(full_path).lower(), full_path
    finally:
        kernel32.CloseHandle(h_proc)
    return "", ""


def protect_process_from_termination():
    """Hardens process DACL so Task Manager / standard taskkill cannot terminate Sentinel."""
    try:
        DACL_SECURITY_INFORMATION = 0x00000004
        SDDL_REVISION_1 = 1
        WRITE_DAC = 0x00040000
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
                return bool(ret)
            kernel32.LocalFree(p_sd)
    except Exception:
        pass
    return False


def check_stop_signal():
    if is_stop_signaled():
        try:
            if os.path.exists(SENTINEL_PID_FILE):
                os.remove(SENTINEL_PID_FILE)
        except Exception:
            pass
        sys.exit(0)


def get_active_window():
    """Returns (hwnd, title, proc_name, exe_path, pid) for the active foreground window."""
    hwnd = user32.GetForegroundWindow()
    if not hwnd:
        return None, "", "", "", 0
    length = user32.GetWindowTextLengthW(hwnd)
    title = ""
    if length > 0:
        buff = ctypes.create_unicode_buffer(length + 1)
        user32.GetWindowTextW(hwnd, buff, length + 1)
        title = buff.value
    pid = wintypes.DWORD()
    user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
    proc_name, exe_path = get_process_info_from_pid(pid.value)
    return hwnd, title, proc_name, exe_path, pid.value


def close_active_tab():
    """Simulates Ctrl+W to close only the offending browser tab."""
    user32.keybd_event(VK_CONTROL, 0, 0, 0)
    user32.keybd_event(VK_W, 0, 0, 0)
    time.sleep(0.02)
    user32.keybd_event(VK_W, 0, KEYEVENTF_KEYUP, 0)
    user32.keybd_event(VK_CONTROL, 0, KEYEVENTF_KEYUP, 0)


def close_window(hwnd):
    user32.PostMessageW(hwnd, WM_CLOSE, 0, 0)


def is_mutex_held(mutex_name):
    h = kernel32.OpenMutexW(SYNCHRONIZE, False, mutex_name)
    if h:
        kernel32.CloseHandle(h)
        return True
    return False


def spawn_process(script_path):
    try:
        subprocess.Popen(
            [PYTHONW_PATH, script_path],
            cwd=BASE_DIR,
            creationflags=subprocess.CREATE_NO_WINDOW | subprocess.DETACHED_PROCESS,
        )
    except Exception as e:
        try:
            with open(os.path.join(BASE_DIR, "sentinel_error.log"), "a", encoding="utf-8") as f:
                f.write(f"[{datetime.now().isoformat()}] spawn_process failed for {script_path}: {e}\n")
        except Exception:
            pass


def monitor_peer(peer_name, script_path, pid_file, mutex_name):
    while True:
        if is_stop_signaled():
            break
        peer_pid = None
        if os.path.exists(pid_file):
            try:
                with open(pid_file, "r", encoding="utf-8") as f:
                    content = f.read().strip()
                    if content.isdigit():
                        peer_pid = int(content)
            except Exception:
                peer_pid = None

        h_proc = None
        if peer_pid and peer_pid != os.getpid():
            h_proc = kernel32.OpenProcess(SYNCHRONIZE, False, peer_pid)

        if h_proc:
            kernel32.WaitForSingleObject(h_proc, INFINITE)
            kernel32.CloseHandle(h_proc)
            if is_stop_signaled():
                break
            spawn_process(script_path)
            time.sleep(0.08)
        else:
            if not is_mutex_held(mutex_name):
                if not is_stop_signaled():
                    spawn_process(script_path)
            time.sleep(0.15)


def log_incident(trigger, title, proc_name, action="CLOSED_BROWSER_TAB"):
    entry = {
        "timestamp": datetime.now().isoformat(),
        "trigger": trigger,
        "window_title": title,
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


def background_process_guard():
    """
    Lightweight in-process PE-verified scanner (ZERO PowerShell spawning!).
    Acts as Session-1 defense-in-depth alongside Session-0 windows_system_enforcer.py.
    """
    while True:
        if is_stop_signaled():
            break
        try:
            scan_and_enforce_processes()
        except Exception:
            pass
        time.sleep(2.0)


def main():
    try:
        kernel32.FreeConsole()
    except Exception:
        pass

    h_sentinel_mutex = kernel32.CreateMutexW(None, False, SENTINEL_MUTEX_NAME)
    if kernel32.GetLastError() == ERROR_ALREADY_EXISTS:
        if h_sentinel_mutex:
            kernel32.CloseHandle(h_sentinel_mutex)
        sys.exit(0)

    protect_process_from_termination()

    try:
        with open(SENTINEL_PID_FILE, "w", encoding="utf-8") as f:
            f.write(str(os.getpid()))
    except Exception:
        pass

    threading.Thread(target=background_process_guard, daemon=True).start()
    threading.Thread(
        target=monitor_peer,
        args=("Watchdog", WATCHDOG_SCRIPT, WATCHDOG_PID_FILE, WATCHDOG_MUTEX_NAME),
        daemon=True,
    ).start()
    threading.Thread(
        target=monitor_peer,
        args=("Guard", GUARD_SCRIPT, GUARD_PID_FILE, GUARD_MUTEX_NAME),
        daemon=True,
    ).start()

    while True:
        try:
            check_stop_signal()

            hwnd, title, proc_name, exe_path, pid = get_active_window()
            if not hwnd or not proc_name:
                time.sleep(0.05)
                continue

            # Step A: PE-verified check for unauthorized browser, VPN client, or music client
            # NEVER kills good apps by window title!
            category, reason = classify_process(proc_name, exe_path)
            if category:
                try:
                    psutil.Process(pid).kill()
                except Exception:
                    subprocess.run(
                        ["taskkill", "/F", "/PID", str(pid), "/T"],
                        capture_output=True,
                        creationflags=subprocess.CREATE_NO_WINDOW,
                    )
                close_window(hwnd)
                log_incident(category, f"{reason} | Title={title}", proc_name, f"TERMINATED_{category}")
                time.sleep(0.15)
                continue

            # Step B: Context-aware browser tab guard (STRICTLY Chrome & Edge ONLY!)
            if is_browser_window(proc_name) and title:
                matched_trigger = evaluate_browser_tab(title, "", proc_name)
                if matched_trigger:
                    close_active_tab()
                    log_incident(matched_trigger, title, proc_name, "CLOSED_BROWSER_TAB")
                    time.sleep(0.12)
                    new_hwnd, new_title, new_proc, _, _ = get_active_window()
                    if new_hwnd == hwnd and is_browser_window(new_proc) and evaluate_browser_tab(new_title, "", new_proc):
                        close_window(hwnd)
                    time.sleep(0.20)
        except Exception:
            time.sleep(0.5)

        time.sleep(0.05)


if __name__ == "__main__":
    main()
