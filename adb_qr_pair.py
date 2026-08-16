import sys
import os
import time
import random
import string
import subprocess
import threading
import tkinter as tk

# Ensure Windows Console supports UTF-8 for ASCII QR Code rendering
if os.name == 'nt':
    os.system('chcp 65001 > nul')
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

import qrcode

ADB_PATH = r"C:\Users\ThinkPad\AppData\Local\Android\Sdk\platform-tools\adb.exe"

def generate_credentials():
    service_id = "ADB_" + ''.join(random.choices(string.ascii_uppercase + string.digits, k=6))
    password = ''.join(random.choices(string.digits, k=6))
    return service_id, password

def run_adb(args):
    try:
        res = subprocess.run([ADB_PATH] + args, capture_output=True, text=True, timeout=10)
        return res.stdout.strip()
    except Exception as e:
        return ""

def scan_mdns_pairing():
    output = run_adb(["mdns", "services"])
    for line in output.splitlines():
        if "_adb-tls-pairing._tcp" in line:
            parts = line.split()
            if len(parts) >= 3:
                return parts[2] # IP:PORT
    return None

def scan_mdns_connect():
    output = run_adb(["mdns", "services"])
    for line in output.splitlines():
        if "_adb-tls-connect._tcp" in line or "_adb._tcp" in line:
            parts = line.split()
            if len(parts) >= 3:
                return parts[2] # IP:PORT
    return None

class QrPopup:
    def __init__(self, qr_matrix, service_name, password):
        self.root = tk.Tk()
        self.root.title("DpcLocker - Scan QR Code on Phone")
        self.root.geometry("380x440")
        self.root.configure(bg="#111827")
        self.root.resizable(False, False)

        title_lbl = tk.Label(
            self.root,
            text="PAIR WITH QR CODE",
            font=("Segoe UI", 14, "bold"),
            fg="#10B981",
            bg="#111827"
        )
        title_lbl.pack(pady=(15, 5))

        sub_lbl = tk.Label(
            self.root,
            text="Scan with phone's camera in Wireless Debugging",
            font=("Segoe UI", 9),
            fg="#9CA3AF",
            bg="#111827"
        )
        sub_lbl.pack(pady=(0, 10))

        # Canvas for Drawing QR Matrix
        modules_count = len(qr_matrix)
        box_size = max(4, 260 // modules_count)
        canvas_size = modules_count * box_size

        canvas = tk.Canvas(self.root, width=canvas_size, height=canvas_size, bg="white", highlightthickness=0)
        canvas.pack(pady=5)

        for r_idx, row in enumerate(qr_matrix):
            for c_idx, cell in enumerate(row):
                if cell:
                    x0 = c_idx * box_size
                    y0 = r_idx * box_size
                    x1 = x0 + box_size
                    y1 = y0 + box_size
                    canvas.create_rectangle(x0, y0, x1, y1, fill="black", outline="black")

        info_lbl = tk.Label(
            self.root,
            text=f"Service: {service_name}  |  Code: {password}",
            font=("Consolas", 10, "bold"),
            fg="#60A5FA",
            bg="#111827"
        )
        info_lbl.pack(pady=(12, 5))

    def close(self):
        try:
            self.root.destroy()
        except Exception:
            pass

def main():
    os.system("cls" if os.name == "nt" else "clear")
    print("=" * 75)
    print(" [#] DPCLOCKER :: INSTANT QR CODE WIRELESS PAIRING")
    print("=" * 75)
    print("\n Instructions for Phone:")
    print("   1. Open: Settings -> Developer Options -> Wireless Debugging")
    print("   2. Tap: 'Pair device with QR code'")
    print("   3. Point your camera at the QR Code on screen:\n")

    service_name, password = generate_credentials()
    qr_payload = f"WIFI:T:ADB;S:{service_name};P:{password};;"

    qr = qrcode.QRCode(border=1)
    qr.add_data(qr_payload)
    qr.make(fit=True)
    qr_matrix = qr.get_matrix()

    # Print in terminal
    qr.print_ascii(invert=True)

    print("\n" + "-" * 75)
    print(f" [*] Service Name : {service_name}")
    print(f" [*] Pairing Key  : {password}")
    print(" [*] Status       : WAITING FOR PHONE TO SCAN QR CODE...")
    print("     (A high-resolution QR popup window is also opened on your screen)")
    print("-" * 75)

    # Launch GUI Popup in main thread / background
    popup = None
    try:
        popup = QrPopup(qr_matrix, service_name, password)
    except Exception as e:
        popup = None

    pairing_result = {"paired": False, "endpoint": None}

    def pairing_worker():
        start_time = time.time()
        while time.time() - start_time < 90:
            endpoint = scan_mdns_pairing()
            if endpoint:
                pairing_result["paired"] = True
                pairing_result["endpoint"] = endpoint
                break
            time.sleep(1)

        if popup:
            popup.close()

    t = threading.Thread(target=pairing_worker, daemon=True)
    t.start()

    if popup:
        try:
            popup.root.mainloop()
        except Exception:
            pass

    t.join(timeout=2)

    if not pairing_result["paired"]:
        print("\n [!] Pairing timeout (90s). Ensure phone and PC are on the same Wi-Fi network.")
        input("\n Press Enter to return to menu...")
        return

    endpoint = pairing_result["endpoint"]
    print(f"\n [+] QR Code Scanned by Phone! Detected endpoint at {endpoint}")
    print(" [*] Sending authentication handshake...")
    pair_res = run_adb(["pair", endpoint, password])
    print(f" [+] Pairing Handshake: {pair_res}")

    print("\n [*] Establishing authenticated connection to device...")
    time.sleep(2)

    connect_endpoint = scan_mdns_connect()
    if connect_endpoint:
        conn_res = run_adb(["connect", connect_endpoint])
        print(f" [+] Connection: {conn_res}")
    else:
        ip = endpoint.split(":")[0]
        print(f" [*] Scanning connect port for {ip}...")
        for _ in range(5):
            time.sleep(1)
            connect_endpoint = scan_mdns_connect()
            if connect_endpoint:
                conn_res = run_adb(["connect", connect_endpoint])
                print(f" [+] Connection: {conn_res}")
                break

    devices_out = run_adb(["devices"])
    if "device" in devices_out and not "offline" in devices_out:
        print("\n" + "=" * 75)
        print(" [OK] QR CODE PAIRING & CONNECTION SUCCESSFUL!")
        print("      Your phone is now permanently paired and connected.")
        print("=" * 75)
    else:
        print("\n [*] Paired! Use Option [1] to connect.")

    input("\n Press Enter to continue...")

if __name__ == "__main__":
    main()
