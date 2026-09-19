"""
==============================================================================
DPCLOCKER :: WINDOWS SENTINEL GUARDIAN WATCHDOG (TRIAD MESH ALPHA)
==============================================================================
Monitors windows_proxy_sentinel.py and windows_sentinel_guard.py in real-time.
Uses zero-latency Win32 kernel handle synchronization (WaitForSingleObject).
Hardened with Win32 DACL anti-termination so it cannot be killed via Task Manager.
Forms an unbreakable 3-node triangular self-healing mesh.
==============================================================================
"""

import ctypes
from ctypes import wintypes
import subprocess
import time
import os
import sys
import threading
import re
from datetime import datetime

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
kernel32.WaitForSingleObject.restype = wintypes.DWORD
kernel32.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]

advapi32.ConvertStringSecurityDescriptorToSecurityDescriptorW.restype = wintypes.BOOL
advapi32.ConvertStringSecurityDescriptorToSecurityDescriptorW.argtypes = [
    wintypes.LPCWSTR, wintypes.DWORD, ctypes.POINTER(wintypes.LPVOID), ctypes.POINTER(wintypes.ULONG)
]
advapi32.SetKernelObjectSecurity.restype = wintypes.BOOL
advapi32.SetKernelObjectSecurity.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.LPVOID]

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
            f.write(f"[{datetime.now().isoformat()}] Watchdog uncaught exception:\n")
            traceback.print_exception(exc_type, exc_value, exc_tb, file=f)
    except Exception:
        pass
sys.excepthook = handle_exception

def protect_process_from_termination():
    """
    Hardens the Watchdog process Security Descriptor (DACL).
    Denies PROCESS_TERMINATE (0x0001) and PROCESS_SUSPEND_RESUME (0x0800)
    to Everyone / standard users so Task Manager and taskkill cannot terminate it.
    """
    try:
        DACL_SECURITY_INFORMATION = 0x00000004
        SDDL_REVISION_1 = 1
        WRITE_DAC = 0x00040000

        # Deny Terminate & Suspend to Everyone (WD) & Built-in Users (BU);
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
                return bool(ret)
            else:
                kernel32.LocalFree(p_sd)
    except Exception:
        pass
    return False

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
                f.write(f"[{datetime.now().isoformat()}] Watchdog spawn_process failed for {script_path}: {e}\n")
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

def main():
    try:
        kernel32.FreeConsole()
    except Exception:
        pass
    # 0. Single instance check via Win32 Named Mutex
    h_watchdog_mutex = kernel32.CreateMutexW(None, False, WATCHDOG_MUTEX_NAME)
    if kernel32.GetLastError() == ERROR_ALREADY_EXISTS:
        if h_watchdog_mutex:
            kernel32.CloseHandle(h_watchdog_mutex)
        sys.exit(0)

    # 1. Apply Win32 DACL anti-termination hardening
    protect_process_from_termination()

    # 2. Record PID
    try:
        with open(WATCHDOG_PID_FILE, "w", encoding="utf-8") as f:
            f.write(str(os.getpid()))
    except Exception:
        pass

    # 3. Start zero-latency kernel monitor threads for Sentinel and Guard Beta
    t_sentinel = threading.Thread(
        target=monitor_peer,
        args=("Sentinel", SENTINEL_SCRIPT, SENTINEL_PID_FILE, SENTINEL_MUTEX_NAME),
        daemon=True
    )
    t_guard = threading.Thread(
        target=monitor_peer,
        args=("Guard", GUARD_SCRIPT, GUARD_PID_FILE, GUARD_MUTEX_NAME),
        daemon=True
    )
    t_sentinel.start()
    t_guard.start()

    # 4. Main liveness loop
    while True:
        if is_stop_signaled():
            try:
                if os.path.exists(WATCHDOG_PID_FILE):
                    os.remove(WATCHDOG_PID_FILE)
            except Exception:
                pass
            sys.exit(0)

        time.sleep(0.5)

if __name__ == "__main__":
    main()
