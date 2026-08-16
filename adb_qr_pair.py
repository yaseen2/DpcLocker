import sys
import os
import time
import random
import string
import subprocess

# Ensure UTF-8 output in Windows Console
if os.name == 'nt':
    os.system('chcp 65001 > nul')
if hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

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

def try_create_popup(qr_matrix, service_name, password):
    try:
        import tkinter as tk
        root = tk.Tk()
        root.title("DpcLocker - QR Code Scanner")
        root.geometry("400x480")
        root.configure(bg="#0F172A")
        root.attributes("-topmost", True)
        root.resizable(False, False)

        title_lbl = tk.Label(
            root,
            text="SCAN WITH YOUR PHONE",
            font=("Segoe UI", 14, "bold"),
            fg="#10B981",
            bg="#0F172A"
        )
        title_lbl.pack(pady=(15, 3))

        sub_lbl = tk.Label(
            root,
            text="Settings -> Developer Options -> Wireless Debugging -> Pair with QR Code",
            font=("Segoe UI", 8),
            fg="#94A3B8",
            bg="#0F172A"
        )
        sub_lbl.pack(pady=(0, 10))

        modules_count = len(qr_matrix)
        box_size = max(4, 280 // modules_count)
        canvas_size = modules_count * box_size

        canvas = tk.Canvas(root, width=canvas_size, height=canvas_size, bg="white", highlightthickness=0)
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
            root,
            text=f"Service: {service_name}   |   Code: {password}",
            font=("Consolas", 10, "bold"),
            fg="#38BDF8",
            bg="#0F172A"
        )
        info_lbl.pack(pady=(10, 5))

        root.update()
        return root
    except Exception as e:
        return None

def main():
    os.system("cls" if os.name == "nt" else "clear")
    print("=" * 75)
    print(" [#] DPCLOCKER :: INSTANT QR CODE WIRELESS PAIRING")
    print("=" * 75)
    print("\n Instructions for your phone:")
    print("   1. Open: Settings -> Developer Options -> Wireless Debugging")
    print("   2. Tap: 'Pair device with QR code'")
    print("   3. Point your camera at the QR Code on screen below:\n")

    service_name, password = generate_credentials()
    qr_payload = f"WIFI:T:ADB;S:{service_name};P:{password};;"

    qr = qrcode.QRCode(border=1)
    qr.add_data(qr_payload)
    qr.make(fit=True)
    qr_matrix = qr.get_matrix()

    # 1. Print in terminal
    qr.print_ascii(invert=True)

    print("\n" + "-" * 75)
    print(f" [*] Service Name : {service_name}")
    print(f" [*] Pairing Code : {password}")
    print(" [*] Status       : WAITING FOR PHONE TO SCAN QR CODE...")
    print("     (A popup window is also opened on your screen)")
    print("-" * 75)

    # 2. Open GUI Popup window
    root = try_create_popup(qr_matrix, service_name, password)

    start_time = time.time()
    paired_endpoint = None

    print("\n [*] Listening for phone's Wi-Fi broadcast...", end="", flush=True)

    while time.time() - start_time < 90:
        if root:
            try:
                root.update_idletasks()
                root.update()
            except Exception:
                root = None

        paired_endpoint = scan_mdns_pairing()
        if paired_endpoint:
            break

        time.sleep(0.5)
        print(".", end="", flush=True)

    if root:
        try:
            root.destroy()
        except Exception:
            pass

    if not paired_endpoint:
        print("\n\n [!] Pairing timed out (90s). Make sure phone and PC are on the same Wi-Fi.")
        input("\n Press Enter to continue...")
        return

    print(f"\n\n [+] QR Code Scanned! Target detected at: {paired_endpoint}")
    print(" [*] Sending authentication handshake...")
    pair_res = run_adb(["pair", paired_endpoint, password])
    print(f" [+] Pairing Result: {pair_res}")

    print("\n [*] Connecting to device...")
    time.sleep(2)

    connect_endpoint = scan_mdns_connect()
    if connect_endpoint:
        conn_res = run_adb(["connect", connect_endpoint])
        print(f" [+] Connection Result: {conn_res}")
    else:
        ip = paired_endpoint.split(":")[0]
        for _ in range(5):
            time.sleep(1)
            connect_endpoint = scan_mdns_connect()
            if connect_endpoint:
                conn_res = run_adb(["connect", connect_endpoint])
                print(f" [+] Connection Result: {conn_res}")
                break

    devices_out = run_adb(["devices"])
    if "device" in devices_out and not "offline" in devices_out:
        print("\n" + "=" * 75)
        print(" [OK] SUCCESS: YOUR PHONE IS NOW PAIRED AND CONNECTED OVER WI-FI!")
        print("=" * 75)
    else:
        print("\n [*] Pairing succeeded! You can now use Option [1] or [2] to connect.")

    input("\n Press Enter to return to menu...")

if __name__ == "__main__":
    main()
