"""
==============================================================================
DPCLOCKER :: WINDOWS SENTINEL GUARDIAN WATCHDOG (SELF-HEALING)
==============================================================================
Monitors windows_proxy_sentinel.py in real-time (<200ms).
If the Sentinel process is ever terminated or killed in Task Manager,
the Watchdog immediately resurrects it in <100ms.
==============================================================================
"""

import subprocess
import time
import os
import sys
import psutil

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SENTINEL_SCRIPT = os.path.join(BASE_DIR, "windows_proxy_sentinel.py")
PYTHONW_PATH = sys.executable.replace("python.exe", "pythonw.exe")

def is_sentinel_running():
    """Checks if windows_proxy_sentinel.py is currently active."""
    for proc in psutil.process_iter(['pid', 'name', 'cmdline']):
        try:
            cmdline = proc.info.get('cmdline') or []
            cmd_str = " ".join(cmdline).lower()
            if "windows_proxy_sentinel.py" in cmd_str:
                return True
        except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
            pass
    return False

def resurrect_sentinel():
    """Launches windows_proxy_sentinel.py via pythonw."""
    try:
        subprocess.Popen(
            [PYTHONW_PATH, SENTINEL_SCRIPT],
            cwd=BASE_DIR,
            creationflags=subprocess.CREATE_NO_WINDOW | subprocess.DETACHED_PROCESS
        )
        print(f"[{time.strftime('%H:%M:%S')}] [+] RESURRECTED: windows_proxy_sentinel.py restored!")
    except Exception as e:
        print(f"Failed to resurrect sentinel: {e}")

def main():
    print("===============================================================================")
    print(" [+] DPCLOCKER :: SENTINEL GUARDIAN WATCHDOG ACTIVE")
    print("===============================================================================")
    print(" Continuously monitoring Sentinel process health. Auto-resurrection active.\n")
    
    while True:
        try:
            if not is_sentinel_running():
                print(f"[{time.strftime('%H:%M:%S')}] [!] Sentinel process termination detected!")
                resurrect_sentinel()
                time.sleep(0.5)
        except Exception:
            pass
            
        time.sleep(0.2)  # Check every 200ms

if __name__ == "__main__":
    main()
